package com.invoice.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具类
 */
public final class WebUtils {

    private static volatile boolean trustForwardedHeaders = false;

    private WebUtils() {
    }

    /**
     * 是否信任 {@code X-Forwarded-For} / {@code X-Real-IP}。
     * 仅在确有可信反向代理改写这些头时开启，否则客户端可伪造 IP 绕过限流与验证码绑定。
     */
    public static void configureTrustForwardedHeaders(boolean trust) {
        trustForwardedHeaders = trust;
    }

    public static boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    /**
     * 提取客户端真实 IP 地址。
     *
     * <p>未开启 {@link #configureTrustForwardedHeaders(boolean)} 时一律使用
     * {@code remoteAddr}。开启后优先级：{@code X-Forwarded-For} 第一段 →
     * {@code X-Real-IP} → {@code remoteAddr}。
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int commaIndex = xff.indexOf(',');
                String candidate = commaIndex >= 0 ? xff.substring(0, commaIndex) : xff;
                candidate = candidate.strip();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.strip();
            }
        }

        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
