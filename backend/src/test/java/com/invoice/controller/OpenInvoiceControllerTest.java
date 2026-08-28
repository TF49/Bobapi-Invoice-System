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
                new BigDecimal("500.00"), "技术服务费", "自动化推单", "PENDING",
                false, null, LocalDateTime.now(), null
        );

        when(invoiceService.createOpenInvoice(
                eq(10L), eq("OUT_20260828001"), any(), eq("测试客户科技公司"),
                eq("91110000MA00000000"), eq(new BigDecimal("500.00")),
                eq("技术服务费"), eq("自动化推单")
        )).thenReturn(response);

        String json = """
                {
                    "outTradeNo": "OUT_20260828001",
                    "companyName": "测试客户科技公司",
                    "taxNumber": "91110000MA00000000",
                    "amount": 500.00,
                    "invoiceType": "技术服务费",
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
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsWhenQuotaInsufficient() throws Exception {
        when(invoiceService.createOpenInvoice(any(), any(), any(), any(), any(), any(), any(), any()))
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
                new BigDecimal("500.00"), "技术服务费", null, "COMPLETED",
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

        when(invoiceService.createInvoicesBatch(eq(10L), any(), any()))
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
        when(invoiceService.createInvoicesBatch(any(), any(), any()))
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