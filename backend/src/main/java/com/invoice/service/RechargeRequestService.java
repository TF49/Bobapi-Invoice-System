package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.invoice.dto.RechargeRequestDto;
import com.invoice.dto.RechargeRequestResponse;
import com.invoice.dto.RechargeReviewDto;
import com.invoice.entity.RechargeRequest;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.RechargeRequestMapper;
import com.invoice.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 充值申请服务
 */
@Service
public class RechargeRequestService {

    private final RechargeRequestMapper rechargeRequestMapper;
    private final UserMapper userMapper;
    private final UserQuotaService userQuotaService;
    private final Path screenshotDir;

    private static final BigDecimal MIN_REQUEST_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_REQUEST_AMOUNT = new BigDecimal("999999.99");
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public RechargeRequestService(RechargeRequestMapper rechargeRequestMapper,
                                   UserMapper userMapper,
                                   UserQuotaService userQuotaService,
                                   @Value("${file.upload-path:./uploads}") String uploadDirectory) {
        this.rechargeRequestMapper = rechargeRequestMapper;
        this.userMapper = userMapper;
        this.userQuotaService = userQuotaService;
        this.screenshotDir = Path.of(uploadDirectory).resolve("screenshots").toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initializeScreenshotDirectory() {
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建充值截图存储目录: " + screenshotDir, e);
        }
    }

    /**
     * 上传充值截图文件
     */
    public String uploadScreenshot(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "上传文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "充值凭证图片大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "仅支持上传 jpg、jpeg、png、webp 格式的图片凭证");
        }

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = screenshotDir.resolve(storedFileName).normalize();

        if (!targetPath.startsWith(screenshotDir)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "非法的文件存储路径");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50000, "保存截图文件失败，请稍后重试");
        }

        return "/recharge-requests/screenshot/" + storedFileName;
    }

    private static final java.util.Map<String, String> IMAGE_MIME_TYPES = java.util.Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp"
    );

    /**
     * 读取充值截图文件资源
     */
    public ScreenshotResource loadScreenshotResource(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "非法的截图文件名");
        }

        Path target = screenshotDir.resolve(fileName).normalize();
        if (!target.startsWith(screenshotDir) || !Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "截图文件不存在");
        }

        try {
            Resource resource = new UrlResource(target.toUri());
            String contentType = resolveImageContentType(fileName, target);
            return new ScreenshotResource(resource, contentType);
        } catch (MalformedURLException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50000, "读取截图资源失败");
        }
    }

    private String resolveImageContentType(String fileName, Path target) {
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        String mimeType = IMAGE_MIME_TYPES.get(extension);
        if (mimeType != null) {
            return mimeType;
        }
        try {
            String probed = Files.probeContentType(target);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ignored) {}
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record ScreenshotResource(Resource resource, String contentType) {}

    /**
     * 用户创建充值申请
     */
    @Transactional
    public RechargeRequest createRequest(Long userId, RechargeRequestDto requestDto) {
        // 验证用户角色
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在");
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只有普通用户可以申请充值");
        }

        // 验证金额
        validateRequestAmount(requestDto.getAmount());

        // 验证截图
        if (requestDto.getScreenshotUrl() == null || requestDto.getScreenshotUrl().trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40006, "请提供充值截图凭证");
        }

        // 创建申请
        RechargeRequest request = new RechargeRequest();
        request.setUserId(userId);
        request.setAmount(requestDto.getAmount());
        request.setScreenshotUrl(requestDto.getScreenshotUrl().trim());
        request.setRemark(requestDto.getRemark() == null ? null : requestDto.getRemark().trim());
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        
        rechargeRequestMapper.insert(request);
        return request;
    }

    /**
     * 获取用户的充值申请列表
     */
    public List<RechargeRequestResponse> getUserRequests(Long userId) {
        List<RechargeRequest> requests = rechargeRequestMapper.selectList(
            new LambdaQueryWrapper<RechargeRequest>()
                .eq(RechargeRequest::getUserId, userId)
                .orderByDesc(RechargeRequest::getCreatedAt)
        );
        
        return requests.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 获取待审核的申请列表（管理员）
     */
    public List<RechargeRequestResponse> getPendingRequests() {
        List<RechargeRequest> requests = rechargeRequestMapper.selectList(
            new LambdaQueryWrapper<RechargeRequest>()
                .eq(RechargeRequest::getStatus, "PENDING")
                .orderByAsc(RechargeRequest::getCreatedAt)
        );
        
        return requests.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有申请列表（管理员）
     */
    public List<RechargeRequestResponse> getAllRequests(String status) {
        LambdaQueryWrapper<RechargeRequest> wrapper = new LambdaQueryWrapper<RechargeRequest>()
            .orderByDesc(RechargeRequest::getCreatedAt);
        
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(RechargeRequest::getStatus, status.trim());
        }
        
        List<RechargeRequest> requests = rechargeRequestMapper.selectList(wrapper);
        
        return requests.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 管理员审核充值申请（带乐观并发控制）
     */
    @Transactional
    public void reviewRequest(Long requestId, RechargeReviewDto reviewDto, Long adminId) {
        RechargeRequest request = rechargeRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "充值申请不存在");
        }
        
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "该申请已被审核");
        }

        // 验证审核状态
        if (!"APPROVED".equals(reviewDto.getStatus()) && !"REJECTED".equals(reviewDto.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "无效的审核状态");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<RechargeRequest> updateWrapper = new LambdaUpdateWrapper<RechargeRequest>()
                .eq(RechargeRequest::getId, requestId)
                .eq(RechargeRequest::getStatus, "PENDING")
                .set(RechargeRequest::getStatus, reviewDto.getStatus())
                .set(RechargeRequest::getAdminRemark, reviewDto.getAdminRemark() == null ? null : reviewDto.getAdminRemark().trim())
                .set(RechargeRequest::getReviewedBy, adminId)
                .set(RechargeRequest::getReviewedAt, now)
                .set(RechargeRequest::getUpdatedAt, now);

        int updated = rechargeRequestMapper.update(null, updateWrapper);
        if (updated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "该申请已被审核或不存在");
        }

        // 如果审核通过，为用户充值
        if ("APPROVED".equals(reviewDto.getStatus())) {
            String idempotencyKey = "RECHARGE_REQUEST_" + requestId;
            userQuotaService.rechargeQuota(
                request.getUserId(),
                request.getAmount(),
                adminId,
                "充值申请审核通过",
                idempotencyKey
            );
        }
    }

    /**
     * 获取待审核申请数量
     */
    public long getPendingCount() {
        return rechargeRequestMapper.selectCount(
            new LambdaQueryWrapper<RechargeRequest>()
                .eq(RechargeRequest::getStatus, "PENDING")
        );
    }

    /**
     * 验证申请金额
     */
    private void validateRequestAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "申请金额必须大于0");
        }
        
        if (amount.compareTo(MIN_REQUEST_AMOUNT) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "申请金额不能小于" + MIN_REQUEST_AMOUNT);
        }
        
        if (amount.compareTo(MAX_REQUEST_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "申请金额不能大于" + MAX_REQUEST_AMOUNT);
        }
        
        if (amount.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "申请金额最多两位小数");
        }
    }

    /**
     * 转换为响应对象
     */
    public RechargeRequestResponse toResponse(RechargeRequest request) {
        RechargeRequestResponse response = new RechargeRequestResponse();
        response.setId(request.getId());
        response.setUserId(request.getUserId());
        response.setAmount(request.getAmount());
        response.setScreenshotUrl(request.getScreenshotUrl());
        response.setStatus(request.getStatus());
        response.setRemark(request.getRemark());
        response.setAdminRemark(request.getAdminRemark());
        response.setReviewedBy(request.getReviewedBy());
        response.setReviewedAt(request.getReviewedAt());
        response.setCreatedAt(request.getCreatedAt());
        response.setUpdatedAt(request.getUpdatedAt());
        
        // 查询用户名
        User user = userMapper.selectById(request.getUserId());
        if (user != null) {
            response.setUsername(user.getUsername());
        }
        
        // 查询审核管理员名
        if (request.getReviewedBy() != null) {
            User admin = userMapper.selectById(request.getReviewedBy());
            if (admin != null) {
                response.setReviewedByName(admin.getUsername());
            }
        }
        
        return response;
    }
}