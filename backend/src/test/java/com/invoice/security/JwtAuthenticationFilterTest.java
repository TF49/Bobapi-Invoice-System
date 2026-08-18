package com.invoice.security;

import com.invoice.entity.User;
import com.invoice.mapper.UserMapper;
import com.invoice.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private UserMapper userMapper;

    private JwtAuthenticationFilter filter;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "TestOnlyInvoiceJwtSecretThatIsLongEnoughForHS256Signing!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "rememberMeExpiration", 60_000L);

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filter, "userMapper", userMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUsingCurrentDatabaseRole() throws Exception {
        User current = user(true, "ADMIN", 2L);
        when(userMapper.selectById(1L)).thenReturn(current);
        String token = jwtUtil.generateToken(1L, "admin", "USER", 2L);

        filter(token);

        JwtUserPrincipal principal = (JwtUserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertEquals("ADMIN", principal.role());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    @Test
    void rejectsTokenAfterAuthenticationVersionChanges() throws Exception {
        when(userMapper.selectById(1L)).thenReturn(user(true, "ADMIN", 3L));
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN", 2L);

        filter(token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsTokenForDisabledUser() throws Exception {
        when(userMapper.selectById(1L)).thenReturn(user(false, "ADMIN", 2L));
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN", 2L);

        filter(token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsLegacyTokenWithoutAuthenticationVersion() throws Exception {
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN", null);

        filter(token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void filter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/admin");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private User user(boolean enabled, String role, Long authVersion) {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole(role);
        user.setEnabled(enabled);
        user.setAuthVersion(authVersion);
        return user;
    }
}
