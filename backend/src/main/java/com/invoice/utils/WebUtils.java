package com.invoice.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.List;

/**
 * Web 请求工具类
 */
public final class WebUtils {

    /**
     * 默认可信代理：仅回环地址（同机 Nginx）。
     *
     * <p>不要默认信任整个私有网段：内网部署时客户端自身就在私有网段内，
     * 会被当作代理跳过，从而采用其自行伪造的 X-Forwarded-For；
     * 云负载均衡、Docker Desktop、docker-proxy 转发的流量也会以私网地址到达。
     * Docker 部署时应精确配置 Nginx 容器的固定 IP。
     */
    public static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.0/8,::1/128";

    private static volatile boolean trustForwardedHeaders = false;
    private static volatile List<IpUtils.Cidr> trustedProxies = IpUtils.parseCidrList(DEFAULT_TRUSTED_PROXIES);

    private WebUtils() {
    }

    /**
     * 是否解析 {@code X-Forwarded-For} / {@code X-Real-IP}。
     * 开启后仍只在直连来源属于可信代理时才读取转发头。
     */
    public static void configureTrustForwardedHeaders(boolean trust) {
        trustForwardedHeaders = trust;
    }

    public static boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    /**
     * 配置可信代理 CIDR 列表（逗号分隔）。为空时使用 {@link #DEFAULT_TRUSTED_PROXIES}。
     */
    public static void configureTrustedProxies(String csv) {
        String value = csv == null || csv.isBlank() ? DEFAULT_TRUSTED_PROXIES : csv;
        trustedProxies = IpUtils.parseCidrList(value);
    }

    /**
     * 提取客户端真实 IP 地址。
     *
     * <ul>
     *   <li>未开启转发头信任，或直连来源（remoteAddr）不是可信代理：直接使用 remoteAddr。</li>
     *   <li>否则从右向左遍历 {@code X-Forwarded-For}，跳过可信代理，取第一个非可信地址。
     *       Nginx 的 {@code $proxy_add_x_forwarded_for} 会把真实来源追加到末尾，
     *       最左侧条目由客户端任意伪造，因此绝不能直接取第一段。</li>
     *   <li>没有可用的 XFF 时回退到 {@code X-Real-IP}。</li>
     * </ul>
     */
    public static String extractClientIp(HttpServletRequest request) {
        String rawRemote = request.getRemoteAddr();
        InetAddress remote = IpUtils.parseLiteral(rawRemote);
        if (remote == null) {
            return rawRemote == null || rawRemote.isBlank() ? "unknown" : rawRemote.strip();
        }
        List<IpUtils.Cidr> proxies = trustedProxies;
        if (!trustForwardedHeaders || !IpUtils.matchesAny(remote, proxies)) {
            return remote.getHostAddress();
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            InetAddress last = remote;
            for (int i = hops.length - 1; i >= 0; i--) {
                InetAddress hop = IpUtils.parseLiteral(hops[i]);
                if (hop == null) {
                    // 非法条目之后的内容不可信，返回最后一个可信链路给出的地址
                    return last.getHostAddress();
                }
                last = hop;
                if (!IpUtils.matchesAny(hop, proxies)) {
                    return hop.getHostAddress();
                }
            }
            return last.getHostAddress();
        }

        InetAddress realIp = IpUtils.parseLiteral(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp.getHostAddress();
        }
        return remote.getHostAddress();
    }
}
