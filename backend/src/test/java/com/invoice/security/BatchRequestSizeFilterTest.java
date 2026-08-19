package com.invoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BatchRequestSizeFilterTest {

    private BatchRequestSizeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BatchRequestSizeFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "maxBatchRequestBytes", 16L);
    }

    @Test
    void rejectsOversizedBatchRequestsWithAConfiguredLimit() throws Exception {
        MockHttpServletRequest request = batchRequest();
        request.setContent("0123456789abcdefg".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("traceId", "trace-size-limit");

        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new AssertionError("oversized request must not reach the filter chain");
            });
        } finally {
            MDC.remove("traceId");
        }

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains(
                "\"code\":41302", "\"traceId\":\"trace-size-limit\"");
    }

    @Test
    void countsTheActualBodyWhenContentLengthIsUnknown() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        configureBatchRequest(request);
        request.setContent("0123456789abcdefg".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request must not reach the filter chain");
        });

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void replaysAnAcceptedBodyToTheControllerChain() throws Exception {
        byte[] body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = batchRequest();
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> forwardedBody = new AtomicReference<>();

        filter.doFilter(request, response, (forwardedRequest, ignoredResponse) ->
                forwardedBody.set(new String(
                        forwardedRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(forwardedBody).hasValue("{\"items\":[]}");
    }

    private MockHttpServletRequest batchRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        configureBatchRequest(request);
        return request;
    }

    private void configureBatchRequest(MockHttpServletRequest request) {
        request.setMethod("POST");
        request.setServletPath("/invoices/batch");
        request.setContentType("application/json");
    }
}
