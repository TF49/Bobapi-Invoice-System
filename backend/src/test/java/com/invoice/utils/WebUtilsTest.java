package com.invoice.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebUtilsTest {

    @AfterEach
    void tearDown() {
        WebUtils.configureTrustForwardedHeaders(false);
    }

    @Test
    void ignoresForwardedHeadersWhenTrustDisabled() {
        WebUtils.configureTrustForwardedHeaders(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");

        assertThat(WebUtils.extractClientIp(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void usesForwardedHeadersWhenTrustEnabled() {
        WebUtils.configureTrustForwardedHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 9.9.9.9");

        assertThat(WebUtils.extractClientIp(request)).isEqualTo("1.2.3.4");
    }
}
