package com.invoice.exception;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.InvoiceRequest;
import com.invoice.dto.BatchInvoiceRowError;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsValidationErrorsToBadRequest() throws Exception {
        mockMvc.perform(post("/test/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"\",\"taxNumber\":\"bad\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void mapsRateLimitsAndSetsRetryAfter() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value(42900));
    }

    @Test
    void returnsStructuredBatchErrorsWithTraceId() throws Exception {
        MDC.put("traceId", "trace-batch-validation");
        try {
            mockMvc.perform(get("/test/batch-validation"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(42202))
                    .andExpect(jsonPath("$.traceId").value("trace-batch-validation"))
                    .andExpect(jsonPath("$.data[0].rowNumber").value(7))
                    .andExpect(jsonPath("$.data[0].field").value("taxNumber"))
                    .andExpect(jsonPath("$.data[0].message").value("税号格式不正确"));
        } finally {
            MDC.remove("traceId");
        }
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @PostMapping("/invoices")
        ApiResponse<Void> validate(@Valid @RequestBody InvoiceRequest request) {
            return ApiResponse.success(null);
        }

        @GetMapping("/rate-limit")
        ApiResponse<Void> rateLimit() {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42900, "请求过于频繁", 30L);
        }

        @GetMapping("/batch-validation")
        ApiResponse<Void> batchValidation() {
            throw new BatchValidationException(List.of(
                    new BatchInvoiceRowError(7, "taxNumber", 42202, "税号格式不正确")));
        }
    }
}
