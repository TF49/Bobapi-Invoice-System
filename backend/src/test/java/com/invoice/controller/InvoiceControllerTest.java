package com.invoice.controller;

import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InvoiceControllerTest {

    @Test
    void rateLimitsRepeatedUploadsBeforeCallingTheService() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        InvoiceController controller = new InvoiceController(invoiceService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "admin", "ADMIN", 0L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.png", "image/png", new byte[]{1});

        for (int attempt = 0; attempt < 20; attempt++) {
            controller.uploadInvoice(1L, file, principal, request);
        }

        assertThatThrownBy(() -> controller.uploadInvoice(1L, file, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42903);
        verify(invoiceService, times(20)).uploadInvoiceFile(1L, file);
    }
}
