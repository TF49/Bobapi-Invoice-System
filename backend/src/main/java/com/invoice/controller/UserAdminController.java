package com.invoice.controller;

import com.invoice.dto.AdminAdjustQuotaRequest;
import com.invoice.dto.AdminCreateUserRequest;
import com.invoice.dto.AdminRechargeQuotaRequest;
import com.invoice.dto.AdminResetPasswordRequest;
import com.invoice.dto.AdminUpdateRoleRequest;
import com.invoice.dto.AdminUpdateStatusRequest;
import com.invoice.dto.AdminUserPageResponse;
import com.invoice.dto.AdminUserResponse;
import com.invoice.dto.ApiResponse;
import com.invoice.dto.UserQuotaResponse;
import com.invoice.dto.UserQuotaTransactionResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserQuotaService;
import com.invoice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/users/admin")
public class UserAdminController {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final UserService userService;
    private final UserQuotaService userQuotaService;
    private final RateLimitService rateLimitService;

    public UserAdminController(UserService userService, UserQuotaService userQuotaService, RateLimitService rateLimitService) {
        this.userService = userService;
        this.userQuotaService = userQuotaService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<AdminUserPageResponse> getUsers(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页最多查询 100 条") int pageSize,
            @RequestParam(required = false) @Size(max = 50, message = "搜索关键词不能超过 50 个字符") String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "^(USER|ADMIN)$", message = "角色只能是 USER 或 ADMIN") String role,
            @RequestParam(required = false) Boolean enabled,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("list", principal.userId(), 120, 42903, "用户列表刷新过于频繁，请稍后再试");
        return ApiResponse.success(userService.getAdminUsers(
                page, pageSize, keyword, role, enabled, principal.userId()));
    }

    @PostMapping
    public ApiResponse<AdminUserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("write", principal.userId(), 30, 42904, "用户管理操作过于频繁，请稍后再试");
        AdminUserResponse user = userService.createAdminUser(
                request.getUsername(), request.getPassword(), request.getRole(), principal.userId());
        return ApiResponse.success("用户创建成功", user);
    }

    @PutMapping("/{id}/role")
    public ApiResponse<AdminUserResponse> updateRole(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody AdminUpdateRoleRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("write", principal.userId(), 30, 42904, "用户管理操作过于频繁，请稍后再试");
        return ApiResponse.success("角色更新成功",
                userService.updateRole(id, request.getRole(), principal.userId()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody AdminUpdateStatusRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("write", principal.userId(), 30, 42904, "用户管理操作过于频繁，请稍后再试");
        return ApiResponse.success("账号状态更新成功",
                userService.updateStatus(id, request.getEnabled(), principal.userId()));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<AdminUserResponse> resetPassword(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody AdminResetPasswordRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("password", principal.userId(), 10, 42905, "密码重置操作过于频繁，请稍后再试");
        return ApiResponse.success("密码重置成功",
                userService.resetPassword(id, request.getPassword(), principal.userId()));
    }

    @PostMapping("/{id}/quota/recharge")
    public ApiResponse<UserQuotaResponse> rechargeQuota(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody AdminRechargeQuotaRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("quota", principal.userId(), 30, 42906, "额度管理操作过于频繁，请稍后再试");
        userQuotaService.rechargeQuota(id, request.getAmount(), principal.userId(), request.getRemark());
        return ApiResponse.success("充值成功", UserQuotaResponse.from(userQuotaService.getUserQuota(id)));
    }

    @PutMapping("/{id}/quota/adjust")
    public ApiResponse<UserQuotaResponse> adjustQuota(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody AdminAdjustQuotaRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("quota", principal.userId(), 30, 42906, "额度管理操作过于频繁，请稍后再试");
        userQuotaService.adjustQuota(id, request.getAmount(), principal.userId(), request.getRemark());
        return ApiResponse.success("调整成功", UserQuotaResponse.from(userQuotaService.getUserQuota(id)));
    }

    @GetMapping("/{id}/quota")
    public ApiResponse<UserQuotaResponse> getUserQuota(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("list", principal.userId(), 120, 42903, "用户列表刷新过于频繁，请稍后再试");
        return ApiResponse.success(UserQuotaResponse.from(userQuotaService.getUserQuota(id)));
    }

    @GetMapping("/{id}/quota/transactions")
    public ApiResponse<List<UserQuotaTransactionResponse>> getUserQuotaTransactions(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @RequestParam(required = false) String transactionType,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("list", principal.userId(), 120, 42903, "用户列表刷新过于频繁，请稍后再试");
        List<UserQuotaTransactionResponse> transactions = userQuotaService.getTransactionHistory(id, transactionType)
                .stream()
                .map(UserQuotaTransactionResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(transactions);
    }

    private void enforceRateLimit(String operation, Long userId, int limit, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "admin-users:" + operation + ":" + userId, limit, RATE_WINDOW);
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}
