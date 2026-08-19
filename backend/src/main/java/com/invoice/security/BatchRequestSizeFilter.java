package com.invoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在 JSON 反序列化前拒绝过大的批量请求，避免无效 payload 占用 MVC 和堆内存。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BatchRequestSizeFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.invoice.batch.max-request-bytes:524288}")
    private long maxBatchRequestBytes = 512L * 1024;

    public BatchRequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isBatchRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxBatchRequestBytes) {
            rejectOversizedRequest(response);
            return;
        }

        int readLimit = Math.toIntExact(Math.min(maxBatchRequestBytes + 1L, Integer.MAX_VALUE));
        byte[] body = request.getInputStream().readNBytes(readLimit);
        if (body.length > maxBatchRequestBytes) {
            rejectOversizedRequest(response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean isBatchRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/invoices/batch".equals(request.getServletPath());
    }

    private void rejectOversizedRequest(HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(41302, "批量请求体不能超过 512KB"));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("异步读取不受支持");
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
