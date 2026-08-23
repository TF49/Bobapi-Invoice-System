package com.invoice.controller;

import com.invoice.dto.AiParseRequest;
import com.invoice.dto.AiParseResponse;
import com.invoice.dto.ApiResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.AiParseService;
import com.invoice.service.StubAiParseService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiParseControllerTest {

    @Test
    void returnsStubResponseWhenAiNotConfigured() {
        AiParseService stubService = new StubAiParseService();
        AiParseController controller = new AiParseController(stubService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "testuser", "USER", 0L);

        AiParseRequest request = new AiParseRequest();
        request.setText("公司名称：测试技术服务公司 税号：91110108MA01TEST99 金额：500.00");

        ApiResponse<AiParseResponse> response = controller.parseInvoice(request, principal);
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getCompanyName()).isNull();
        assertThat(response.getData().getConfidence()).isEqualTo("LOW");
        assertThat(response.getData().getHint()).contains("AI 识别服务暂未开通");
    }

    @Test
    void returnsExtractedInvoiceDataSuccessfully() {
        AiParseService mockService = mock(AiParseService.class);
        when(mockService.parse(anyString())).thenReturn(new AiParseResponse(
                "北京科技有限公司",
                "91110108MA01TEST99",
                new BigDecimal("1234.50"),
                "HIGH",
                null
        ));

        AiParseController controller = new AiParseController(mockService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "testuser", "USER", 0L);

        AiParseRequest request = new AiParseRequest();
        request.setText("发票抬头：北京科技有限公司，税号：91110108MA01TEST99，开票金额：1234.50元");

        ApiResponse<AiParseResponse> response = controller.parseInvoice(request, principal);
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getCompanyName()).isEqualTo("北京科技有限公司");
        assertThat(response.getData().getTaxNumber()).isEqualTo("91110108MA01TEST99");
        assertThat(response.getData().getAmount()).isEqualByComparingTo("1234.50");
        assertThat(response.getData().getConfidence()).isEqualTo("HIGH");
    }

    @Test
    void rateLimitsExcessiveAiRequestsPerUser() {
        AiParseService mockService = mock(AiParseService.class);
        when(mockService.parse(anyString())).thenReturn(new AiParseResponse(null, null, null, "LOW", null));

        AiParseController controller = new AiParseController(mockService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "testuser", "USER", 0L);

        AiParseRequest request = new AiParseRequest();
        request.setText("测试文本");

        for (int i = 0; i < 20; i++) {
            controller.parseInvoice(request, principal);
        }

        assertThatThrownBy(() -> controller.parseInvoice(request, principal))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42905);
    }

    @Test
    void verifiesInvoiceDataSuccessfully() {
        AiParseService mockService = mock(AiParseService.class);
        when(mockService.verify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiParseResponse(
                        "北京科技有限公司（核查）",
                        "91110108MA01TEST99",
                        new BigDecimal("1234.50"),
                        "HIGH",
                        null
                ));

        AiParseController controller = new AiParseController(mockService, new RateLimitService());
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "testuser", "USER", 0L);

        com.invoice.dto.AiVerifyRequest request = new com.invoice.dto.AiVerifyRequest();
        request.setText("发票抬头：北京科技有限公司，税号：91110108MA01TEST99，开票金额：1234.50元");
        request.setCompanyName("北京科技有限公司");
        request.setTaxNumber("91110108MA01TEST99");
        request.setAmount(new BigDecimal("1234.50"));

        ApiResponse<AiParseResponse> response = controller.verifyInvoice(request, principal);
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getCompanyName()).isEqualTo("北京科技有限公司（核查）");
    }
}
