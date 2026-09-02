package com.invoice.controller;

import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.dto.BatchInvoiceRequest;
import com.invoice.dto.InvoiceResponse;
import com.invoice.entity.Invoice;
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

    @Test
    void serializesInvoiceResponseWithExpectedFieldNames() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        InvoiceResponse response = new InvoiceResponse(
                1L, "测试公司", "91410100MAE5H38A0F", new java.math.BigDecimal("100.00"),
                "技术服务费", "", "COMPLETED", true, "NONE", null, null, null, null, null, 2L, "user",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                true, true, "invoice.png"
        );
        String json = mapper.writeValueAsString(response);
        System.out.println("SERIALIZED JSON: " + json);
        org.assertj.core.api.Assertions.assertThat(json).contains("\"isProcessed\":true");

        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), Invoice.class);
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Invoice> wrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.set(Invoice::getIsProcessed, true);
        System.out.println("LAMBDA SQL SET: " + wrapper.getSqlSet());
        org.assertj.core.api.Assertions.assertThat(wrapper.getSqlSet()).contains("is_processed=");
    }

    @Test
    void delegatesUpdateInvoiceProcessedToService() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        InvoiceController controller = new InvoiceController(invoiceService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(8L, "user", "USER", 0L);
        com.invoice.dto.UpdateInvoiceProcessedRequest req = new com.invoice.dto.UpdateInvoiceProcessedRequest(true);

        controller.updateInvoiceProcessed(15L, req, principal);

        verify(invoiceService).updateInvoiceProcessed(15L, 8L, false, true);
    }

    @Test
    void delegatesBatchUpdateInvoiceProcessedToService() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        InvoiceController controller = new InvoiceController(invoiceService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(8L, "user", "USER", 0L);
        com.invoice.dto.BatchUpdateProcessedRequest req = new com.invoice.dto.BatchUpdateProcessedRequest(List.of(15L, 16L), true);

        controller.batchUpdateInvoiceProcessed(req, principal);

        verify(invoiceService).batchUpdateInvoiceProcessed(List.of(15L, 16L), 8L, false, true);
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
