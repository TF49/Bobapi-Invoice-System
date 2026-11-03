package com.invoice.config;

import com.invoice.utils.IpUtils;
import com.invoice.utils.WebUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientIpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClientIpConfiguration.class);

    /** 超过该范围的可信网段视为过宽（IPv4 /24、IPv6 /64），启动时告警 */
    private static final int BROAD_IPV4_PREFIX = 24;
    private static final int BROAD_IPV6_PREFIX = 64;

    @Value("${app.client-ip.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Value("${app.client-ip.trusted-proxies:" + WebUtils.DEFAULT_TRUSTED_PROXIES + "}")
    private String trustedProxies;

    @PostConstruct
    void apply() {
        // 非法 CIDR 直接启动失败，避免静默退化为不安全配置
        WebUtils.configureTrustedProxies(trustedProxies);
        WebUtils.configureTrustForwardedHeaders(trustForwardedHeaders);
        log.info("Client IP resolution: trustForwardedHeaders={}, trustedProxies={}",
                trustForwardedHeaders, trustedProxies);
        if (trustForwardedHeaders) {
            warnIfBroad(trustedProxies);
        }
    }

    private void warnIfBroad(String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String item : csv.split(",")) {
            String entry = item.strip();
            if (entry.isEmpty()) {
                continue;
            }
            IpUtils.Cidr cidr = IpUtils.Cidr.parse(entry);
            boolean loopback = isLoopback(cidr);
            int threshold = cidr.network().length == 4 ? BROAD_IPV4_PREFIX : BROAD_IPV6_PREFIX;
            if (!loopback && cidr.prefixLength() < threshold) {
                log.warn("Trusted proxy range {} is broad: any client inside it can spoof X-Forwarded-For. "
                        + "Prefer the exact IP of the reverse proxy (e.g. the Nginx container).", entry);
            }
        }
    }

    private static boolean isLoopback(IpUtils.Cidr cidr) {
        byte[] network = cidr.network();
        if (network.length == 4) {
            return (network[0] & 0xff) == 127 && cidr.prefixLength() >= 8;
        }
        return cidr.prefixLength() == 128 && network[15] == 1 && allZero(network, 15);
    }

    private static boolean allZero(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
