package com.invoice.controller;

import com.invoice.config.SecurityConfig;
import com.invoice.dto.RechargeRequestResponse;
import com.invoice.entity.RechargeRequest;
import com.invoice.exception.GlobalExceptionHandler;
import com.invoice.security.ApiKeyAuthenticationFilter;
import com.invoice.security.JwtAuthenticationFilter;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.service.RechargeRequestService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RechargeRequestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}
        )
)
@Import({GlobalExceptionHandler.class, RechargeRequestControllerTest.TestSecurityConfig.class})
class RechargeRequestControllerTest {

    private static final String IDEMPOTENCY_KEY = "recharge-1234567890123456";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RechargeRequestService rechargeRequestService;

    @Test
    void createsRequestFromFeeAndReturnsBothAmounts() throws Exception {
        RechargeRequest entity = new RechargeRequest();
        entity.setId(8L);
        RechargeRequestResponse response = new RechargeRequestResponse();
        response.setId(8L);
        response.setFeeAmount(new BigDecimal("300.00"));
        response.setAmount(new BigDecimal("10000.00"));
        response.setStatus("PENDING");

        when(rechargeRequestService.createRequest(eq(1L), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(entity);
        when(rechargeRequestService.toResponse(entity)).thenReturn(response);

        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(300.00))
                .andExpect(jsonPath("$.data.amount").value(10000.00));

        verify(rechargeRequestService).createRequest(eq(1L), any(), eq(IDEMPOTENCY_KEY));
    }

    @Test
    void rejectsMissingOrMalformedIdempotencyKey() throws Exception {
        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsLegacyAmountFieldAndInvalidFees() throws Exception {
        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeAmount\":300.00,\"amount\":999999.99,\"screenshotUrl\":\"/proof.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeAmount\":100.001,\"screenshotUrl\":\"/proof.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeAmount\":30000.00,\"screenshotUrl\":\"/proof.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsMissingScreenshot() throws Exception {
        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeAmount\":300.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rateLimitsCreateRequests() throws Exception {
        when(rechargeRequestService.createRequest(eq(1L), any(), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new com.invoice.exception.BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS, 42908,
                        "充值申请提交过于频繁，请稍后再试", 23L));

        mockMvc.perform(post("/recharge-requests")
                        .with(authentication(authUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "23"))
                .andExpect(jsonPath("$.code").value(42908));
    }

    private String validJson() {
        return "{\"feeAmount\":300.00,\"screenshotUrl\":\"/proof.png\",\"remark\":\"test\"}";
    }

    private UsernamePasswordAuthenticationToken authUser() {
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "user", "USER", 0L);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.sendError(HttpStatus.UNAUTHORIZED.value()))
                            .accessDeniedHandler((request, response, exception) ->
                                    response.sendError(HttpStatus.FORBIDDEN.value())))
                    .build();
        }
    }
}
