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
import com.invoice.security.RateLimitService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private final RateLimitService rateLimitService;
    private final Path screenshotDir;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");
    private static final BigDecimal MIN_FEE_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_FEE_AMOUNT = new BigDecimal("29999.99");
    private static final BigDecimal MAX_QUOTA_AMOUNT = new BigDecimal("999999.99");
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public RechargeRequestService(RechargeRequestMapper rechargeRequestMapper,
                                   UserMapper userMapper,
                                   UserQuotaService userQuotaService,
                                   RateLimitService rateLimitService,
                                   @Value("${file.upload-path:./uploads}") String uploadDirectory) {
        this.rechargeRequestMapper = rechargeRequestMapper;
        this.userMapper = userMapper;
        this.userQuotaService = userQuotaService;
        this.rateLimitService = rateLimitService;
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
    public RechargeRequest createRequest(Long userId, RechargeRequestDto requestDto, String idempotencyKey) {
        // 验证用户角色
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在");
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只有普通用户可以申请充值");
        }

        // 只信任用户提交的手续费，最终额度统一由服务端计算。
        BigDecimal feeAmount = requestDto.getFeeAmount();
        validateFeeAmount(feeAmount);
        BigDecimal quotaAmount = calculateQuotaAmount(feeAmount);
        String screenshotUrl = requestDto.getScreenshotUrl() == null ? null : requestDto.getScreenshotUrl().trim();
        String remark = normalizeRemark(requestDto.getRemark());

        // 验证截图
        if (screenshotUrl == null || screenshotUrl.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40006, "请提供充值截图凭证");
        }

        RechargeRequest existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return validateRepeatedRequest(existing, feeAmount, screenshotUrl, remark);
        }

        enforceCreateRateLimit(userId);

        // 创建申请
        RechargeRequest request = new RechargeRequest();
        request.setUserId(userId);
        request.setFeeAmount(feeAmount);
        request.setAmount(quotaAmount);
        request.setIdempotencyKey(idempotencyKey);
        request.setScreenshotUrl(screenshotUrl);
        request.setRemark(remark);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        
        try {
            rechargeRequestMapper.insert(request);
            return request;
        } catch (DuplicateKeyException exception) {
            RechargeRequest concurrentlyCreated = findByIdempotencyKey(userId, idempotencyKey);
            if (concurrentlyCreated == null) {
                throw exception;
            }
            return validateRepeatedRequest(concurrentlyCreated, feeAmount, screenshotUrl, remark);
        }
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
     * 验证手续费并按 3% 的固定比例计算最终额度
     */
    private void validateFeeAmount(BigDecimal feeAmount) {
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "手续费必须大于0");
        }
        
        if (feeAmount.compareTo(MIN_FEE_AMOUNT) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "手续费不能小于" + MIN_FEE_AMOUNT);
        }
        
        if (feeAmount.compareTo(MAX_FEE_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "手续费不能大于" + MAX_FEE_AMOUNT);
        }
        
        if (feeAmount.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "手续费最多两位小数");
        }
    }

    private BigDecimal calculateQuotaAmount(BigDecimal feeAmount) {
        BigDecimal quotaAmount = feeAmount.divide(FEE_RATE, 2, RoundingMode.DOWN);
        if (quotaAmount.compareTo(MAX_QUOTA_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "申请额度不能大于" + MAX_QUOTA_AMOUNT);
        }
        return quotaAmount;
    }

    private RechargeRequest findByIdempotencyKey(Long userId, String idempotencyKey) {
        return rechargeRequestMapper.selectOne(
                new LambdaQueryWrapper<RechargeRequest>()
                        .eq(RechargeRequest::getUserId, userId)
                        .eq(RechargeRequest::getIdempotencyKey, idempotencyKey)
        );
    }

    private void enforceCreateRateLimit(Long userId) {
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "recharge-request:create:" + userId, 10, Duration.ofMinutes(1));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42908,
                    "充值申请提交过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }
    }

    private RechargeRequest validateRepeatedRequest(RechargeRequest existing, BigDecimal feeAmount,
                                                    String screenshotUrl, String remark) {
        boolean samePayload = existing.getFeeAmount() != null
                && existing.getFeeAmount().compareTo(feeAmount) == 0
                && Objects.equals(existing.getScreenshotUrl(), screenshotUrl)
                && Objects.equals(normalizeRemark(existing.getRemark()), remark);
        if (!samePayload) {
            throw new BusinessException(HttpStatus.CONFLICT, 40903,
                    "Idempotency-Key 已用于其他充值申请");
        }
        return existing;
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 转换为响应对象
     */
    public RechargeRequestResponse toResponse(RechargeRequest request) {
        RechargeRequestResponse response = new RechargeRequestResponse();
        response.setId(request.getId());
        response.setUserId(request.getUserId());
        response.setFeeAmount(request.getFeeAmount());
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
