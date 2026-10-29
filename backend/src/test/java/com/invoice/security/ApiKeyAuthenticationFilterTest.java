package com.invoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.entity.User;
import com.invoice.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new ApiKeyAuthenticationFilter(userMapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidApiKey() throws Exception {
        User user = new User();
        user.setId(88L);
        user.setUsername("api_merchant");
        user.setRole("USER");
        user.setEnabled(true);
        user.setApiKeyEnabled(true);
        user.setApiKey("bk_live_valid_key");
        user.setAuthVersion(1L);

        when(userMapper.selectOne(any())).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/invoices");
        request.addHeader("X-API-KEY", "bk_live_valid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        JwtUserPrincipal principal = (JwtUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(88L, principal.userId());
        assertEquals("api_merchant", principal.username());
    }

    @Test
    void rejectsMissingApiKeyOnOpenApiRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/invoices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40101"));
    }

    @Test
    void authenticatesValidApiKeyUnderContextPath() throws Exception {
        User user = new User();
        user.setId(88L);
        user.setUsername("api_merchant");
        user.setRole("USER");
        user.setEnabled(true);
        user.setApiKeyEnabled(true);
        user.setAuthVersion(1L);
        when(userMapper.selectOne(any())).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/open/v1/documents");
        request.setContextPath("/api");
        request.addHeader("X-API-KEY", "bk_live_valid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ignoresApiKeyOnNonOpenApiRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/my");
        request.setContextPath("/api");
        request.addHeader("X-API-KEY", "bk_live_valid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // 不查库、不认证，交给后续 JWT 过滤器和安全规则处理
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userMapper);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ignoresAdminApiKeyOnAdminRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/users/admin/5");
        request.setContextPath("/api");
        request.addHeader("X-API-KEY", "bk_live_admin_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userMapper);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsDisabledApiKey() throws Exception {
        User user = new User();
        user.setId(88L);
        user.setUsername("api_merchant");
        user.setRole("USER");
        user.setEnabled(true);
        user.setApiKeyEnabled(false);
        user.setApiKey("bk_live_disabled_key");

        when(userMapper.selectOne(any())).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/invoices");
        request.addHeader("X-API-KEY", "bk_live_disabled_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("40101"));
    }
}