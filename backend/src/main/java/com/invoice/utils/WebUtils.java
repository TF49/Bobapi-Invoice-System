package com.invoice.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具类
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 提取客户端真实 IP 地址。
     *
     * <p>优先级：{@code X-Forwarded-For} 第一段 → {@code X-Real-IP} → {@code remoteAddr}。
     *
     * <p><strong>注意</strong>：生产环境部署在反向代理（Nginx 等）后面时，必须在代理侧正确设置
     * {@code proxy_set_header X-Real-IP $remote_addr}，否则客户端可以通过伪造
     * {@code X-Forwarded-For} 头绕过基于 IP 的限流和锁定机制。
     */
    public static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For 可能包含多个 IP（逗号分隔），取第一个（最原始的客户端 IP）
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

        return request.getRemoteAddr();
    }
}
