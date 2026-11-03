package com.invoice.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebUtilsTest {

    /** 模拟 Nginx 容器的固定 IP */
    private static final String NGINX_IP = "10.0.0.5";

    @AfterEach
    void tearDown() {
        WebUtils.configureTrustForwardedHeaders(false);
        WebUtils.configureTrustedProxies(null);
    }

    @Test
    void ignoresForwardedHeadersWhenTrustDisabled() {
        WebUtils.configureTrustForwardedHeaders(false);
        WebUtils.configureTrustedProxies(NGINX_IP);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(NGINX_IP);
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");

        assertThat(WebUtils.extractClientIp(request)).isEqualTo(NGINX_IP);
    }

    @Test
    void usesRightmostUntrustedForwardedHopWhenTrustEnabled() {
        WebUtils.configureTrustForwardedHeaders(true);
        WebUtils.configureTrustedProxies(NGINX_IP);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(NGINX_IP);
        // 最左侧 1.2.3.4 由客户端伪造，9.9.9.9 是 Nginx 追加的真实来源
        request.addHeader("X-Forwarded-For", "1.2.3.4, 9.9.9.9");

        assertThat(WebUtils.extractClientIp(request)).isEqualTo("9.9.9.9");
    }

    @Test
    void ignoresForwardedHeadersFromUntrustedPeer() {
        WebUtils.configureTrustForwardedHeaders(true);
        WebUtils.configureTrustedProxies(NGINX_IP);
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 绕过 Nginx 直连后端端口的客户端
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");

        assertThat(WebUtils.extractClientIp(request)).isEqualTo("198.51.100.7");
    }
}
