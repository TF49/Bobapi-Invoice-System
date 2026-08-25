package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.LoginRequest;
import com.invoice.dto.LoginResponse;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.security.LoginAttemptService;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserService;
import com.invoice.utils.JwtUtil;
import com.invoice.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          LoginAttemptService loginAttemptService, RateLimitService rateLimitService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, 
                                            HttpServletRequest httpRequest) {
        String clientIp = getClientIP(httpRequest);
        // 获取客户端标识（IP + 用户名组合）
        String clientKey = clientIp + ":" + request.getUsername().toLowerCase(Locale.ROOT);

        // 1. 优先检查是否已被防爆破机制锁定（避免被较短周期的速率限制覆盖准确的锁定剩余时间）
        if (loginAttemptService.isLocked(clientKey)) {
            long remainingTime = loginAttemptService.getRemainingLockTime(clientKey);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42901,
                    "登录失败次数过多，请 " + remainingTime + " 秒后再试", Math.max(1, remainingTime));
        }

        // 2. 单 IP 维度限流，防止针对不同用户名的密码喷洒攻击（Password Spraying）
        RateLimitService.RateLimitResult ipRateLimit = rateLimitService.tryAcquire(
                "login:ip:" + clientIp, 30, Duration.ofMinutes(1));
        if (!ipRateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42900,
                    "登录请求过于频繁，请稍后再试", ipRateLimit.retryAfterSeconds());
        }

        // 3. IP + 用户名维度限流
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "login:" + clientKey, 10, Duration.ofMinutes(1));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42900,
                    "登录请求过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }
        
        User user = userService.findByUsername(request.getUsername());
        
        // 统一错误信息并防御时间盲注（Timing Attack），防止通过耗时探测用户是否存在
        if (user == null) {
            userService.validateDummyPassword(request.getPassword());
            loginAttemptService.loginFailed(clientKey);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }
        
        // 检查用户是否已被删除
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            recordLoginFailed(clientKey, user);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }
        
        if (!Boolean.TRUE.equals(user.getEnabled())
                || !userService.validatePassword(request.getPassword(), user.getPassword())) {
            recordLoginFailed(clientKey, user);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }
        
        // 登录成功，清除失败记录
        loginAttemptService.loginSucceeded(clientKey);
        
        // 生成 token（支持记住我）
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getAuthVersion(), request.getRememberMe());
        LoginResponse response = new LoginResponse(token, user.getUsername(), user.getRole());
        
        return ApiResponse.success("登录成功", response);
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody com.invoice.dto.RegisterRequest request,
                                               HttpServletRequest httpRequest) {
        // 按客户端 IP 限流，防止批量注册
        String clientIp = WebUtils.extractClientIp(httpRequest);
        RateLimitService.RateLimitResult registerLimit = rateLimitService.tryAcquire(
                "register:ip:" + clientIp, 5, Duration.ofMinutes(1));
        if (!registerLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42906,
                    "注册请求过于频繁，请稍后再试", registerLimit.retryAfterSeconds());
        }

        // 检查用户名是否已存在
        if (userService.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "用户名已存在");
        }
        
        // 创建新用户（默认为普通用户）
        User user = userService.createUser(request.getUsername(), request.getPassword(), "USER");
        
        // 生成 token
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getAuthVersion());
        LoginResponse response = new LoginResponse(token, user.getUsername(), user.getRole());
        
        return ApiResponse.success("注册成功", response);
    }
    
    /**
     * 获取客户端 IP，支持反向代理（X-Forwarded-For / X-Real-IP）。
     */
    private String getClientIP(HttpServletRequest request) {
        return WebUtils.extractClientIp(request);
    }

    private void recordLoginFailed(String clientKey, User user) {
        if (user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "INVOICE_CLERK".equalsIgnoreCase(user.getRole()))) {
            loginAttemptService.adminLoginFailed(clientKey);
        } else {
            loginAttemptService.loginFailed(clientKey);
        }
    }
}
