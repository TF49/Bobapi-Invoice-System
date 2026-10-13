package com.invoice.controller;

import com.invoice.dto.CaptchaVerifyRequest;
import com.invoice.dto.LoginRequest;
import com.invoice.dto.RegisterRequest;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.security.LoginAttemptService;
import com.invoice.security.RateLimitService;
import com.invoice.security.SlideCaptchaService;
import com.invoice.service.UserService;
import com.invoice.utils.JwtUtil;
import com.invoice.utils.WebUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerCaptchaTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SlideCaptchaService slideCaptchaService;

    private AuthController controller;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        WebUtils.configureTrustForwardedHeaders(false);
        controller = new AuthController(
                userService, jwtUtil, loginAttemptService, rateLimitService, slideCaptchaService);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.10");
        // 多数用例默认放行；captchaRequiredIsRateLimited 会覆盖为拒绝
        lenient().when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
    }

    @AfterEach
    void tearDown() {
        WebUtils.configureTrustForwardedHeaders(false);
    }

    @Test
    void captchaRequiredIsRateLimited() {
        when(rateLimitService.tryAcquire(eq("captcha:required:203.0.113.10"), eq(30), any()))
                .thenReturn(new RateLimitService.RateLimitResult(false, 12));

        assertThatThrownBy(() -> controller.captchaRequired("alice", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询过于频繁");
    }

    @Test
    void loginConsumesCaptchaWhenRequired() {
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(loginAttemptService.requiresCaptcha(anyString())).thenReturn(true);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setRole("USER");
        user.setEnabled(true);
        user.setDeleted(0);
        user.setAuthVersion(1L);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(userService.validatePassword("password1", "hash")).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyLong(), any()))
                .thenReturn("jwt");

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password1");
        request.setRememberMe(true);
        request.setCaptchaToken("tok123");

        controller.login(request, httpRequest);

        verify(slideCaptchaService).consumeToken("tok123", "203.0.113.10");
    }

    @Test
    void registerChecksUsernameBeforeConsumingCaptcha() {
        when(userService.findByUsername("taken")).thenReturn(new User());

        RegisterRequest request = new RegisterRequest();
        request.setUsername("taken");
        request.setPassword("pass1234");
        request.setCaptchaToken("tok-abc");

        assertThatThrownBy(() -> controller.register(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");

        verify(slideCaptchaService, never()).consumeToken(anyString(), anyString());
    }

    @Test
    void registerConsumesCaptchaAfterUsernameAvailable() {
        when(userService.findByUsername("newbie")).thenReturn(null);
        User created = new User();
        created.setId(9L);
        created.setUsername("newbie");
        created.setRole("USER");
        created.setAuthVersion(1L);
        when(userService.createUser("newbie", "pass1234", "USER")).thenReturn(created);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyLong())).thenReturn("jwt");

        RegisterRequest request = new RegisterRequest();
        request.setUsername("newbie");
        request.setPassword("pass1234");
        request.setCaptchaToken("tok-ok");

        var response = controller.register(request, httpRequest);

        verify(slideCaptchaService).consumeToken("tok-ok", "203.0.113.10");
        assertThat(response.getData().getToken()).isEqualTo("jwt");
    }

    @Test
    void verifyCaptchaDelegatesWithClientIp() {
        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setCaptchaId("cid");
        request.setX(40);
        request.setY(12);
        when(slideCaptchaService.verifyAndIssueToken("cid", 40, 12, "203.0.113.10"))
                .thenReturn("issued-token");

        var response = controller.verifyCaptcha(request, httpRequest);
        assertThat(response.getData().getCaptchaToken()).isEqualTo("issued-token");
    }
}
