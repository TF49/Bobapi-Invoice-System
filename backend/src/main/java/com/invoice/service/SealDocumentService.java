package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.invoice.dto.SealDocumentResponse;
import com.invoice.entity.SealDocument;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.SealDocumentMapper;
import com.invoice.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 盖章文件服务（含 CAS 乐观条件更新防并发与前置扣款）
 */
@Service
public class SealDocumentService {

    private static final Logger log = LoggerFactory.getLogger(SealDocumentService.class);

    private final SealDocumentMapper sealDocumentMapper;
    private final UserSealQuotaService userSealQuotaService;
    private final UserMapper userMapper;
    private final Path uploadRoot;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 禁止上传的高危可执行程序及脚本扩展名
     */
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "sh", "cmd", "ps1", "vbs", "msi", "dll", "com", "scr",
            "jsp", "jspx", "php", "asp", "aspx"
    );

    public record FileDownload(Resource resource, String fileName, String contentType, long contentLength) {}

    public SealDocumentService(SealDocumentMapper sealDocumentMapper,
                               UserSealQuotaService userSealQuotaService,
                               UserMapper userMapper,
                               @Value("${file.upload-path:./uploads}") String uploadDirectory) {
        this.sealDocumentMapper = sealDocumentMapper;
        this.userSealQuotaService = userSealQuotaService;
        this.userMapper = userMapper;
        this.uploadRoot = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot.resolve("documents/source"));
            Files.createDirectories(uploadRoot.resolve("documents/sealed"));
        } catch (IOException e) {
            log.error("创建盖章文件上传目录失败", e);
        }
    }

    /**
     * 带幂等键的上传结果。duplicate=true 表示命中已有记录，本次没有重复扣费和落盘。
     */
    public record UploadResult(SealDocumentResponse document, boolean duplicate) {}

    /**
     * 用户上传待盖章文件（无需表单，格式不限，原子前置扣款 10 元）
     */
    @Transactional
    public SealDocumentResponse uploadSourceDocument(Long userId, MultipartFile file) {
        return uploadSourceDocument(userId, file, null).document();
    }

    /**
     * 上传待盖章文件，按 (userId, idempotencyKey) 防重。
     * 相同幂等键 + 文件名、大小、内容 SHA-256 均相同：直接返回已有记录，不再扣费；
     * 相同幂等键但文件不同（包括同名不同内容）：拒绝，避免调用方误用幂等键。
     */
    @Transactional
    public UploadResult uploadSourceDocument(Long userId, MultipartFile file, String idempotencyKey) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "请选择待上传的文件");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document"
        );
        if (originalFilename.contains("/")) {
            originalFilename = originalFilename.substring(originalFilename.lastIndexOf('/') + 1);
        }
        if (originalFilename.contains("\\")) {
            originalFilename = originalFilename.substring(originalFilename.lastIndexOf('\\') + 1);
        }
        if (originalFilename.isBlank()) {
            originalFilename = "document";
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }

        // 安全防范：拒绝危险可执行程序或恶意脚本
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40007, "系统不支持上传可执行程序或脚本文件，请上传证明材料文档或图片");
        }

        // 内容哈希：防重时识别"同名但内容不同"的文件，同时留作审计
        String sourceSha256 = sha256Hex(file);

        String normalizedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        if (normalizedKey != null) {
            SealDocument existing = sealDocumentMapper.selectOne(new LambdaQueryWrapper<SealDocument>()
                    .eq(SealDocument::getUserId, userId)
                    .eq(SealDocument::getIdempotencyKey, normalizedKey));
            if (existing != null) {
                // V28 之前写入的记录没有哈希，只能退回到文件名 + 大小比较
                boolean sameContent = existing.getSourceFileSha256() == null
                        || existing.getSourceFileSha256().equalsIgnoreCase(sourceSha256);
                boolean sameFile = Objects.equals(existing.getSourceFileName(), originalFilename)
                        && Objects.equals(existing.getSourceFileSize(), file.getSize())
                        && sameContent;
                if (!sameFile) {
                    throw new BusinessException(HttpStatus.CONFLICT, 40903,
                            "Idempotency-Key 已用于其他文件，请为新文件使用新的 Idempotency-Key");
                }
                return new UploadResult(toResponse(existing, null), true);
            }
        }

        // 1. 先插入待盖章记录，获取主键 ID（幂等键唯一索引兜住并发重复提交）
        SealDocument document = new SealDocument();
        document.setUserId(userId);
        document.setIdempotencyKey(normalizedKey);
        document.setSourceFileName(originalFilename);
        document.setSourceFilePath(""); // 稍后落盘后写入真实路径
        document.setSourceFileSize(file.getSize());
        document.setSourceFileSha256(sourceSha256);
        document.setSourceFileExt(extension);
        document.setFeeAmount(UserSealQuotaService.SINGLE_SEAL_FEE);
        document.setStatus(STATUS_PENDING);
        document.setIsProcessed(false);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        try {
            sealDocumentMapper.insert(document);
        } catch (DuplicateKeyException exception) {
            // 同一幂等键的另一个请求刚刚写入。当前事务的读快照可能看不到它，直接提示调用方稍后查询。
            throw new BusinessException(HttpStatus.CONFLICT, 40904,
                    "相同 Idempotency-Key 的上传请求正在处理，请稍后通过列表接口查询结果");
        }

        // 2. 前置扣除 10 元盖章额度（排他锁校验并扣减，余额不足直接抛出异常终止，不产生物理文件）
        userSealQuotaService.deductQuota(
                userId, document.getId(), UserSealQuotaService.SINGLE_SEAL_FEE,
                "申请文件盖章扣费（" + originalFilename + "）"
        );

        // 3. 落盘保存文件到物理磁盘
        String dateSubDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path targetDir = uploadRoot.resolve("documents/source").resolve(dateSubDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "创建存储目录失败");
        }

        String savedFileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;
        Path targetPath = targetDir.resolve(savedFileName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 异常清理可能产生的残缺文件
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "文件保存失败");
        }

        // 4. 更新物理路径
        document.setSourceFilePath(uploadRoot.relativize(targetPath).toString().replace('\\', '/'));
        sealDocumentMapper.updateById(document);

        return new UploadResult(toResponse(document, null), false);
    }

    /**
     * 获取当前用户的盖章文件列表
     */
    public List<SealDocumentResponse> getMyDocuments(Long userId, String status) {
        LambdaQueryWrapper<SealDocument> wrapper = new LambdaQueryWrapper<SealDocument>()
                .eq(SealDocument::getUserId, userId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(SealDocument::getStatus, status.toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(SealDocument::getId);

        List<SealDocument> list = sealDocumentMapper.selectList(wrapper);
        return toResponseList(list);
    }

    /**
     * 管理员/开票员获取全部盖章文件列表
     */
    public List<SealDocumentResponse> getAllDocuments(String status, String username) {
        LambdaQueryWrapper<SealDocument> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(SealDocument::getStatus, status.toUpperCase(Locale.ROOT));
        }
        if (username != null && !username.isBlank()) {
            List<User> matchedUsers = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, username.trim())
            );
            if (matchedUsers.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> userIds = matchedUsers.stream().map(User::getId).toList();
            wrapper.in(SealDocument::getUserId, userIds);
        }
        wrapper.orderByDesc(SealDocument::getId);

        List<SealDocument> list = sealDocumentMapper.selectList(wrapper);
        return toResponseList(list);
    }

    /**
     * 用户撤销待盖章文件（CAS 乐观条件更新防重复退费）
     */
    @Transactional
    public SealDocumentResponse cancelDocument(Long id, Long currentUserId, boolean isAdmin) {
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!isAdmin && !Objects.equals(document.getUserId(), currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权撤销他人的文件");
        }
        if (!STATUS_PENDING.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "只能撤销待盖章状态的文件");
        }

        // 使用 CAS 状态条件更新，受影响行数不为 1 时说明已被并发处理
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<SealDocument> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SealDocument::getId, id)
                .eq(SealDocument::getStatus, STATUS_PENDING)
                .set(SealDocument::getStatus, STATUS_CANCELLED)
                .set(SealDocument::getUpdatedAt, now);

        int updated = sealDocumentMapper.update(null, updateWrapper);
        if (updated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "该文件已被处理或撤销，无法重复取消");
        }

        // CAS 更新成功后，退回 10 元额度
        userSealQuotaService.refundQuota(
                document.getUserId(), document.getId(), document.getFeeAmount(),
                "撤销盖章申请退费（" + document.getSourceFileName() + "）"
        );

        document.setStatus(STATUS_CANCELLED);
        document.setUpdatedAt(now);
        return toResponse(document, null);
    }

    /**
     * 管理员/开票员驳回盖章文件（CAS 乐观条件更新防重复退款）
     */
    @Transactional
    public SealDocumentResponse rejectDocument(Long id, Long operatorId, String reason) {
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!STATUS_PENDING.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "当前状态不是待盖章，无法驳回");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<SealDocument> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SealDocument::getId, id)
                .eq(SealDocument::getStatus, STATUS_PENDING)
                .set(SealDocument::getStatus, STATUS_REJECTED)
                .set(SealDocument::getRejectReason, reason != null ? reason.trim() : null)
                .set(SealDocument::getOperatorId, operatorId)
                .set(SealDocument::getUpdatedAt, now);

        int updated = sealDocumentMapper.update(null, updateWrapper);
        if (updated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "该文件已被处理或撤销，无法重复驳回");
        }

        // 自动退费 10 元给申请用户
        userSealQuotaService.refundQuota(
                document.getUserId(), document.getId(), document.getFeeAmount(),
                "管理员驳回盖章申请退款（原因：" + reason + "）"
        );

        document.setStatus(STATUS_REJECTED);
        document.setRejectReason(reason);
        document.setOperatorId(operatorId);
        document.setUpdatedAt(now);
        return toResponse(document, null);
    }

    /**
     * 管理员/开票员上传盖章后交付文件（CAS 条件更新防状态错乱）
     */
    @Transactional
    public SealDocumentResponse uploadSealedDocument(Long id, MultipartFile file, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "请选择已盖章的交付文件");
        }
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!STATUS_PENDING.equals(document.getStatus()) && !STATUS_COMPLETED.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "已取消或已驳回的文件不能上传交付件");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "sealed_document"
        );
        if (originalFilename.contains("/")) {
            originalFilename = originalFilename.substring(originalFilename.lastIndexOf('/') + 1);
        }
        if (originalFilename.contains("\\")) {
            originalFilename = originalFilename.substring(originalFilename.lastIndexOf('\\') + 1);
        }

        String dateSubDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path targetDir = uploadRoot.resolve("documents/sealed").resolve(dateSubDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "创建交付目录失败");
        }

        String savedFileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;
        Path targetPath = targetDir.resolve(savedFileName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "交付文件保存失败");
        }

        LocalDateTime now = LocalDateTime.now();
        String relativePath = uploadRoot.relativize(targetPath).toString().replace('\\', '/');

        // 使用条件更新，确保目标状态未在上传期间被撤销或驳回
        LambdaUpdateWrapper<SealDocument> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SealDocument::getId, id)
                .in(SealDocument::getStatus, List.of(STATUS_PENDING, STATUS_COMPLETED))
                .set(SealDocument::getStatus, STATUS_COMPLETED)
                .set(SealDocument::getSealedFileName, originalFilename)
                .set(SealDocument::getSealedFilePath, relativePath)
                .set(SealDocument::getSealedFileSize, file.getSize())
                .set(SealDocument::getOperatorId, operatorId)
                .set(SealDocument::getCompletedAt, now)
                .set(SealDocument::getUpdatedAt, now);

        int updated = sealDocumentMapper.update(null, updateWrapper);
        if (updated != 1) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "该文件已被用户撤销或驳回，无法完成交付");
        }

        document.setSealedFileName(originalFilename);
        document.setSealedFilePath(relativePath);
        document.setSealedFileSize(file.getSize());
        document.setStatus(STATUS_COMPLETED);
        document.setOperatorId(operatorId);
        document.setCompletedAt(now);
        document.setUpdatedAt(now);

        return toResponse(document, null);
    }

    /**
     * 用户标记处理状态
     */
    @Transactional
    public void updateProcessed(Long id, Long currentUserId, boolean isAdminOrClerk, Boolean isProcessed) {
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!isAdminOrClerk && !Objects.equals(document.getUserId(), currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权操作他人的文件");
        }
        // 仅更新处理标记列。若用 updateById 回写整条旧实体，会把并发交付写入的
        // COMPLETED 状态与交付文件字段覆盖回读取时的 PENDING 快照。
        LambdaUpdateWrapper<SealDocument> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SealDocument::getId, id)
                .set(SealDocument::getIsProcessed, Boolean.TRUE.equals(isProcessed))
                .set(SealDocument::getUpdatedAt, LocalDateTime.now());
        sealDocumentMapper.update(null, updateWrapper);
    }

    /**
     * 获取待盖章工单数量
     */
    public long getPendingCount() {
        return sealDocumentMapper.selectCount(
                new LambdaQueryWrapper<SealDocument>().eq(SealDocument::getStatus, STATUS_PENDING)
        );
    }

    /**
     * 下载原始待盖章文件
     */
    public FileDownload downloadSourceFile(Long id, Long currentUserId, boolean isAdminOrClerk) {
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!isAdminOrClerk && !Objects.equals(document.getUserId(), currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权下载该文件");
        }

        Path file = uploadRoot.resolve(document.getSourceFilePath()).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "物理文件不存在或已被移除");
        }

        Resource resource = new FileSystemResource(file);
        String contentType = probeContentType(file, document.getSourceFileName());
        long length = 0;
        try {
            length = Files.size(file);
        } catch (IOException ignored) {}

        return new FileDownload(resource, document.getSourceFileName(), contentType, length);
    }

    /**
     * 预览已盖章交付文件
     */
    public FileDownload previewSealedFile(Long id, Long currentUserId, boolean isAdminOrClerk) {
        return getSealedFileDownload(id, currentUserId, isAdminOrClerk);
    }

    /**
     * 下载已盖章交付文件
     */
    public FileDownload downloadSealedFile(Long id, Long currentUserId, boolean isAdminOrClerk) {
        return getSealedFileDownload(id, currentUserId, isAdminOrClerk);
    }

    private FileDownload getSealedFileDownload(Long id, Long currentUserId, boolean isAdminOrClerk) {
        SealDocument document = sealDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "文件记录不存在");
        }
        if (!isAdminOrClerk && !Objects.equals(document.getUserId(), currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权查看该文件");
        }
        if (document.getSealedFilePath() == null || document.getSealedFilePath().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40006, "该文件尚未盖章完成");
        }

        Path file = uploadRoot.resolve(document.getSealedFilePath()).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "已盖章物理文件不存在");
        }

        Resource resource = new FileSystemResource(file);
        String contentType = probeContentType(file, document.getSealedFileName());
        long length = 0;
        try {
            length = Files.size(file);
        } catch (IOException ignored) {}

        return new FileDownload(resource, document.getSealedFileName(), contentType, length);
    }

    private String sha256Hex(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "读取上传文件失败");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private String probeContentType(Path path, String filename) {
        String ext = "";
        int idx = filename != null ? filename.lastIndexOf('.') : -1;
        if (idx >= 0 && idx < filename.length() - 1) {
            ext = filename.substring(idx + 1).toLowerCase(Locale.ROOT);
        }
        switch (ext) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "webp": return "image/webp";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                try {
                    String probed = Files.probeContentType(path);
                    if (probed != null) return probed;
                } catch (IOException ignored) {}
                return "application/octet-stream";
        }
    }

    private List<SealDocumentResponse> toResponseList(List<SealDocument> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        for (SealDocument d : list) {
            if (d.getUserId() != null) userIds.add(d.getUserId());
            if (d.getOperatorId() != null) userIds.add(d.getOperatorId());
        }
        Map<Long, String> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, User::getUsername, (k1, k2) -> k1));
        }
        final Map<Long, String> finalUserMap = userMap;
        return list.stream().map(d -> toResponse(d, finalUserMap)).collect(Collectors.toList());
    }

    private SealDocumentResponse toResponse(SealDocument d, Map<Long, String> userMap) {
        SealDocumentResponse resp = new SealDocumentResponse();
        resp.setId(d.getId());
        resp.setUserId(d.getUserId());
        resp.setSourceFileName(d.getSourceFileName());
        resp.setSourceFileSize(d.getSourceFileSize());
        resp.setSourceFileExt(d.getSourceFileExt());
        resp.setSealedFileName(d.getSealedFileName());
        resp.setSealedFileSize(d.getSealedFileSize());
        resp.setFeeAmount(d.getFeeAmount());
        resp.setStatus(d.getStatus());
        resp.setRejectReason(d.getRejectReason());
        resp.setOperatorId(d.getOperatorId());
        resp.setIsProcessed(d.getIsProcessed());
        resp.setCompletedAt(d.getCompletedAt());
        resp.setCreatedAt(d.getCreatedAt());
        resp.setUpdatedAt(d.getUpdatedAt());

        if (userMap != null) {
            resp.setUsername(userMap.get(d.getUserId()));
            resp.setOperatorName(userMap.get(d.getOperatorId()));
        } else {
            if (d.getUserId() != null) {
                User u = userMapper.selectById(d.getUserId());
                if (u != null) resp.setUsername(u.getUsername());
            }
            if (d.getOperatorId() != null) {
                User u = userMapper.selectById(d.getOperatorId());
                if (u != null) resp.setOperatorName(u.getUsername());
            }
        }
        return resp;
    }
}
