package com.invoice.security;

import com.invoice.exception.BusinessException;
import com.invoice.utils.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录注册相关的限流策略：验证码请求、验证码失败锁定、注册频率与注册配额。
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    static final int CAPTCHA_GENERATE_IP_PER_MINUTE = 10;
    static final int CAPTCHA_GENERATE_IP_PER_HOUR = 120;
    static final int CAPTCHA_GENERATE_SUBNET_PER_MINUTE = 60;
    static final int CAPTCHA_VERIFY_IP_PER_MINUTE = 15;
    static final int CAPTCHA_REQUIRED_IP_PER_MINUTE = 30;
    static final int REGISTER_ATTEMPT_IP_PER_10_MINUTES = 10;

    private final RateLimitService rateLimitService;
    private final int captchaFailureLimit;
    private final int registerIpHourlyLimit;
    private final int registerIpDailyLimit;
    private final int registerSubnetDailyLimit;
    private final int registerGlobalHourlyLimit;

    @Autowired
    public AuthRateLimiter(RateLimitService rateLimitService,
                           @Value("${app.security.captcha.failure-limit:10}") int captchaFailureLimit,
                           @Value("${app.security.register.ip-hourly-limit:3}") int registerIpHourlyLimit,
                           @Value("${app.security.register.ip-daily-limit:10}") int registerIpDailyLimit,
                           @Value("${app.security.register.subnet-daily-limit:30}") int registerSubnetDailyLimit,
                           @Value("${app.security.register.global-hourly-limit:60}") int registerGlobalHourlyLimit) {
        this.rateLimitService = rateLimitService;
        this.captchaFailureLimit = captchaFailureLimit;
        this.registerIpHourlyLimit = registerIpHourlyLimit;
        this.registerIpDailyLimit = registerIpDailyLimit;
        this.registerSubnetDailyLimit = registerSubnetDailyLimit;
        this.registerGlobalHourlyLimit = registerGlobalHourlyLimit;
    }

    /** 使用默认配额（测试与无配置场景） */
    public AuthRateLimiter(RateLimitService rateLimitService) {
        this(rateLimitService, 10, 3, 10, 30, 60);
    }

    public void checkCaptchaGenerate(String ip) {
        ensureNotLockedByCaptchaFailures(ip);
        acquire("captcha:gen:" + ip, CAPTCHA_GENERATE_IP_PER_MINUTE, MINUTE,
                42907, "验证码请求过于频繁，请稍后再试");
        acquire("captcha:gen:h:" + ip, CAPTCHA_GENERATE_IP_PER_HOUR, HOUR,
                42907, "验证码请求过于频繁，请稍后再试");
        acquire("captcha:gen:net:" + IpUtils.subnetKey(ip), CAPTCHA_GENERATE_SUBNET_PER_MINUTE, MINUTE,
                42907, "当前网络验证码请求过于频繁，请稍后再试");
    }

    public void checkCaptchaVerify(String ip) {
        ensureNotLockedByCaptchaFailures(ip);
        acquire("captcha:verify:" + ip, CAPTCHA_VERIFY_IP_PER_MINUTE, MINUTE,
                42908, "验证请求过于频繁，请稍后再试");
    }

    public void checkCaptchaRequiredQuery(String ip) {
        acquire("captcha:required:" + ip, CAPTCHA_REQUIRED_IP_PER_MINUTE, MINUTE,
                42909, "查询过于频繁，请稍后再试");
    }

    /**
     * 记录一次滑块失败；10 分钟内失败次数达到上限后，该 IP 暂停获取与提交验证码。
     */
    public void recordCaptchaFailure(String ip) {
        if (captchaFailureLimit <= 0) {
            return;
        }
        RateLimitService.RateLimitResult result =
                rateLimitService.hit("captcha:fail:" + ip, captchaFailureLimit, TEN_MINUTES);
        if (result != null && !result.allowed()) {
            log.warn("Captcha failure limit reached: ip={}", ip);
        }
    }

    /**
     * 注册尝试频率检查，并预检注册配额（不消耗），避免配额已满时白白核销用户的验证码。
     */
    public void checkRegisterAttempt(String ip) {
        acquire("register:attempt:" + ip, REGISTER_ATTEMPT_IP_PER_10_MINUTES, TEN_MINUTES,
                42906, "注册请求过于频繁，请稍后再试");
        for (Quota quota : registrationQuotas(ip)) {
            RateLimitService.RateLimitResult result = rateLimitService.check(quota.key(), quota.limit(), quota.window());
            if (result != null && !result.allowed()) {
                throw quota.exception(result.retryAfterSeconds());
            }
        }
    }

    /**
     * 占用注册配额（IP 每小时/每天、网段每天、全站每小时）。任一配额不足时归还已占用部分并抛出 429。
     * 用户创建失败时调用 {@link RegistrationPermit#release()} 归还。
     */
    public RegistrationPermit acquireRegistration(String ip) {
        List<Quota> acquired = new ArrayList<>();
        for (Quota quota : registrationQuotas(ip)) {
            RateLimitService.RateLimitResult result =
                    rateLimitService.tryAcquire(quota.key(), quota.limit(), quota.window());
            if (result != null && !result.allowed()) {
                acquired.forEach(q -> rateLimitService.release(q.key(), q.window()));
                if (quota.global()) {
                    log.warn("Global registration limit reached ({} per hour)", quota.limit());
                }
                throw quota.exception(result.retryAfterSeconds());
            }
            acquired.add(quota);
        }
        return () -> acquired.forEach(q -> rateLimitService.release(q.key(), q.window()));
    }

    private List<Quota> registrationQuotas(String ip) {
        List<Quota> quotas = new ArrayList<>(4);
        if (registerIpHourlyLimit > 0) {
            quotas.add(new Quota("register:ip:h:" + ip, registerIpHourlyLimit, HOUR,
                    "该网络注册账号过多，请稍后再试", false));
        }
        if (registerIpDailyLimit > 0) {
            quotas.add(new Quota("register:ip:d:" + ip, registerIpDailyLimit, DAY,
                    "该网络今日注册账号已达上限", false));
        }
        if (registerSubnetDailyLimit > 0) {
            quotas.add(new Quota("register:net:d:" + IpUtils.subnetKey(ip), registerSubnetDailyLimit, DAY,
                    "该网段今日注册账号已达上限", false));
        }
        if (registerGlobalHourlyLimit > 0) {
            quotas.add(new Quota("register:global:h", registerGlobalHourlyLimit, HOUR,
                    "当前注册人数过多，请稍后再试", true));
        }
        return quotas;
    }

    private void ensureNotLockedByCaptchaFailures(String ip) {
        if (captchaFailureLimit <= 0) {
            return;
        }
        RateLimitService.RateLimitResult result =
                rateLimitService.check("captcha:fail:" + ip, captchaFailureLimit, TEN_MINUTES);
        if (result != null && !result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42910,
                    "安全验证失败次数过多，请 " + result.retryAfterSeconds() + " 秒后再试",
                    result.retryAfterSeconds());
        }
    }

    private void acquire(String key, int limit, Duration window, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, window);
        if (result != null && !result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }

    @FunctionalInterface
    public interface RegistrationPermit {
        void release();
    }

    private record Quota(String key, int limit, Duration window, String message, boolean global) {
        BusinessException exception(long retryAfterSeconds) {
            return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42906, message, retryAfterSeconds);
        }
    }
}
