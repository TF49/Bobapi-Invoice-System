package com.invoice.controller;

import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.dto.BatchInvoiceRequest;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

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

    @Test
    void rateLimitsBatchCreationByUser() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        InvoiceController controller = new InvoiceController(invoiceService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "user", "USER", 0L);
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        BatchInvoiceRequest batch = batchRequest();

        for (int attempt = 0; attempt < 3; attempt++) {
            controller.createInvoicesBatch(
                    batch, "batch-1234567890123456", principal, request);
        }

        assertThatThrownBy(() -> controller.createInvoicesBatch(
                batch, "batch-1234567890123456", principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42902);
        verify(invoiceService, times(3)).createInvoicesBatch(
                1L, "batch-1234567890123456", batch.getItems());
    }

    @Test
    void rateLimitsBatchCreationByIpAcrossUsers() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        InvoiceController controller = new InvoiceController(invoiceService, new RateLimitService());
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        BatchInvoiceRequest batch = batchRequest();

        for (long userId = 1; userId <= 20; userId++) {
            controller.createInvoicesBatch(
                    batch,
                    "batch-1234567890123456",
                    new JwtUserPrincipal(userId, "user" + userId, "USER", 0L),
                    request);
        }

        assertThatThrownBy(() -> controller.createInvoicesBatch(
                batch,
                "batch-1234567890123456",
                new JwtUserPrincipal(21L, "user21", "USER", 0L),
                request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42902);
        verify(invoiceService, times(20)).createInvoicesBatch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private BatchInvoiceRequest batchRequest() {
        BatchInvoiceItemRequest item = new BatchInvoiceItemRequest();
        item.setRowNumber(2);
        item.setCompanyName("示例公司");
        item.setTaxNumber("ABCDE12345678901");
        item.setAmount("100.00");
        BatchInvoiceRequest request = new BatchInvoiceRequest();
        request.setItems(List.of(item));
        return request;
    }

    private MockHttpServletRequest requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }
}
