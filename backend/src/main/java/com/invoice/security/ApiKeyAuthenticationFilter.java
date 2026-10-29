package com.invoice.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.dto.ApiResponse;
import com.invoice.entity.User;
import com.invoice.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * OpenAPI 专用 API Key 认证过滤器
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(UserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        // API Key 只用于 OpenAPI。非 /open/ 路径一律不认 API Key，交给 JWT 认证，
        // 避免 Key 泄露后被用来调用站内接口或管理后台接口。
        if (!isOpenApiPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getHeader("X-Api-Key");
        }

        if (apiKey != null && !apiKey.isBlank()) {
            apiKey = apiKey.trim();
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getApiKey, apiKey)
                            .eq(User::getDeleted, 0)
            );

            if (user != null && Boolean.TRUE.equals(user.getEnabled()) && Boolean.TRUE.equals(user.getApiKeyEnabled())) {
                JwtUserPrincipal principal = new JwtUserPrincipal(
                        user.getId(), user.getUsername(), user.getRole(), user.getAuthVersion());
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                log.warn("API Key authentication failed for URI {}: userExists={}, enabled={}, apiKeyEnabled={}",
                        requestUri, user != null, user != null ? user.getEnabled() : null,
                        user != null ? user.getApiKeyEnabled() : null);
                writeSecurityError(response, 401, 40101, "API Key 无效或未启用");
                return;
            }
        } else if (!isDocUri(requestUri)) {
            // 没有携带 API Key 且是 OpenAPI 接口
            writeSecurityError(response, 401, 40101, "缺少 X-API-KEY 请求头");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 去掉 context-path（如 /api）后判断是否为 /open/ 开头的 OpenAPI 路径
     */
    private boolean isOpenApiPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return path.startsWith("/open/");
    }

    private boolean isDocUri(String uri) {
        return uri.contains("/doc.html") || uri.contains("/v3/api-docs") || uri.contains("/swagger");
    }

    private void writeSecurityError(HttpServletResponse response,
                                    int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}