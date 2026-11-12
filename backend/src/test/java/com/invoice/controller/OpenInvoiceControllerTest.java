package com.invoice.controller;

import com.invoice.config.SecurityConfig;
import com.invoice.dto.OpenInvoiceResponse;
import com.invoice.exception.BusinessException;
import com.invoice.exception.GlobalExceptionHandler;
import com.invoice.security.ApiKeyAuthenticationFilter;
import com.invoice.security.JwtAuthenticationFilter;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OpenInvoiceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}
        )
)
@Import({GlobalExceptionHandler.class, OpenInvoiceControllerTest.TestSecurityConfig.class})
class OpenInvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
    }

    private UsernamePasswordAuthenticationToken authUser() {
        JwtUserPrincipal principal = new JwtUserPrincipal(10L, "potato_client", "USER", 1L);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void allowsUserToCreateInvoiceViaOpenApi() throws Exception {
        OpenInvoiceResponse response = new OpenInvoiceResponse(
                1001L, "OUT_20260828001", "测试客户科技公司", "91110000MA00000000",
                new BigDecimal("500.00"), "技术服务费", "NORMAL", "自动化推单", "PENDING", "API",
                "NONE", null, null, null, null,
                false, null, LocalDateTime.now(), null
        );

        when(invoiceService.createOpenInvoice(
                eq(10L), eq("OUT_20260828001"), any(), eq("测试客户科技公司"),
                eq("91110000MA00000000"), eq(new BigDecimal("500.00")),
                eq("技术服务费"), eq("NORMAL"), eq("DEFAULT"), eq("自动化推单"),
                eq("标准版"), eq("项"), eq("3%")
        )).thenReturn(response);

        String json = """
                {
                    "outTradeNo": "OUT_20260828001",
                    "companyName": "测试客户科技公司",
                    "taxNumber": "91110000MA00000000",
                    "specification": "标准版",
                    "unit": "项",
                    "amount": 500.00,
                    "invoiceType": "技术服务费",
                    "taxRate": "3%",
                    "remark": "自动化推单"
                }
                """;

        mockMvc.perform(post("/open/v1/invoices")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.outTradeNo").value("OUT_20260828001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submissionType").value("API"));
    }

    /**
     * OPENAPI.md 的 Python 示例按字符串传金额（避免浮点），需保证服务端可接受。
     */
    @Test
    void acceptsAmountAsDecimalString() throws Exception {
        OpenInvoiceResponse response = new OpenInvoiceResponse(
                1002L, "OUT_20260828003", "测试客户科技公司", "91110000MA00000000",
                new BigDecimal("1888.88"), "技术服务费", "NORMAL", null, "PENDING", "API",
                "NONE", null, null, null, null,
                false, null, LocalDateTime.now(), null
        );

        when(invoiceService.createOpenInvoice(
                eq(10L), eq("OUT_20260828003"), any(), eq("测试客户科技公司"),
                any(), eq(new BigDecimal("1888.88")),
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(response);

        String json = """
                {
                    "outTradeNo": "OUT_20260828003",
                    "companyName": "测试客户科技公司",
                    "amount": "1888.88"
                }
                """;

        mockMvc.perform(post("/open/v1/invoices")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1002));
    }

    /**
     * 未定义字段会被拒绝（spring.jackson.deserialization.fail-on-unknown-properties=true），文档 FAQ 依赖此行为。
     */
    @Test
    void rejectsUnknownRequestField() throws Exception {
        String json = """
                {
                    "outTradeNo": "OUT_20260828004",
                    "companyName": "测试客户科技公司",
                    "amount": 500.00,
                    "spec": "标准版"
                }
                """;

        mockMvc.perform(post("/open/v1/invoices")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void rejectsWhenQuotaInsufficient() throws Exception {
        when(invoiceService.createOpenInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 40002, "额度不足，当前余额：100，需要：500"));

        String json = """
                {
                    "outTradeNo": "OUT_20260828002",
                    "companyName": "测试客户科技公司",
                    "amount": 500.00
                }
                """;

        mockMvc.perform(post("/open/v1/invoices")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("额度不足，当前余额：100，需要：500"));
    }

    @Test
    void queriesInvoiceByOutTradeNo() throws Exception {
        OpenInvoiceResponse response = new OpenInvoiceResponse(
                1001L, "OUT_20260828001", "测试客户科技公司", "91110000MA00000000",
                new BigDecimal("500.00"), "技术服务费", "NORMAL", null, "COMPLETED", "API",
                "NONE", null, null, null, null,
                true, "invoice_20260828.png", LocalDateTime.now(), LocalDateTime.now()
        );

        when(invoiceService.getOpenInvoiceByOutTradeNo(10L, "OUT_20260828001"))
                .thenReturn(response);

        mockMvc.perform(get("/open/v1/invoices/by-out-trade-no/OUT_20260828001")
                        .with(authentication(authUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.outTradeNo").value("OUT_20260828001"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.downloadable").value(true));
    }

    @Test
    void allowsUserToBatchCreateInvoicesViaOpenApi() throws Exception {
        com.invoice.dto.BatchInvoiceResponse batchResponse = new com.invoice.dto.BatchInvoiceResponse();
        batchResponse.setBatchId(55L);
        batchResponse.setTotal(2);
        batchResponse.setSuccessCount(2);
        batchResponse.setFailureCount(0);
        batchResponse.setTotalAmount("800.00");
        batchResponse.setItems(List.of(
                new com.invoice.dto.BatchInvoiceItemResult(2, 201L, "SUCCESS", "创建成功"),
                new com.invoice.dto.BatchInvoiceItemResult(3, 202L, "SUCCESS", "创建成功")
        ));

        when(invoiceService.createInvoicesBatch(eq(10L), any(), any(), eq("API")))
                .thenReturn(batchResponse);

        String json = """
                {
                    "items": [
                        {
                            "rowNumber": 2,
                            "companyName": "测试客户科技公司",
                            "taxNumber": "91110000MA00000000",
                            "amount": "500.00",
                            "invoiceType": "技术服务费"
                        },
                        {
                            "rowNumber": 3,
                            "companyName": "测试客户科技公司B",
                            "taxNumber": "91110000MA00000001",
                            "amount": "300.00",
                            "invoiceType": "AI订阅服务费"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/open/v1/invoices/batch")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.batchId").value(55))
                .andExpect(jsonPath("$.data.totalAmount").value("800.00"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void rejectsBatchCreateWhenQuotaInsufficient() throws Exception {
        when(invoiceService.createInvoicesBatch(any(), any(), any(), eq("API")))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 40002, "额度不足，当前余额：100，需要：800"));

        String json = """
                {
                    "items": [
                        {
                            "rowNumber": 2,
                            "companyName": "测试客户科技公司",
                            "amount": "800.00"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/open/v1/invoices/batch")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("额度不足，当前余额：100，需要：800"));
    }

    @Test
    void cancelsInvoiceByIdViaOpenApi() throws Exception {
        OpenInvoiceResponse response = new OpenInvoiceResponse(
                1001L, "OUT_20260828001", "测试客户科技公司", "91110000MA00000000",
                new BigDecimal("500.00"), "技术服务费", "NORMAL", null, "CANCELLED", "API",
                "NONE", null, null, null, null,
                false, null, LocalDateTime.now(), null
        );

        when(invoiceService.cancelOpenInvoice(10L, 1001L)).thenReturn(response);

        mockMvc.perform(post("/open/v1/invoices/1001/cancel")
                        .with(authentication(authUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelsInvoiceByOutTradeNoViaOpenApi() throws Exception {
        OpenInvoiceResponse response = new OpenInvoiceResponse(
                1001L, "OUT_20260828001", "测试客户科技公司", "91110000MA00000000",
                new BigDecimal("500.00"), "技术服务费", "NORMAL", null, "CANCELLED", "API",
                "NONE", null, null, null, null,
                false, null, LocalDateTime.now(), null
        );

        when(invoiceService.cancelOpenInvoiceByOutTradeNo(10L, "OUT_20260828001")).thenReturn(response);

        mockMvc.perform(post("/open/v1/invoices/by-out-trade-no/OUT_20260828001/cancel")
                        .with(authentication(authUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
