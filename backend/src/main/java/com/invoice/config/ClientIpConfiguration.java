package com.invoice.config;

import com.invoice.utils.WebUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientIpConfiguration {

    @Value("${app.client-ip.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @PostConstruct
    void apply() {
        WebUtils.configureTrustForwardedHeaders(trustForwardedHeaders);
    }
}
