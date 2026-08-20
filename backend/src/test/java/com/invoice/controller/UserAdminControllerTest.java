package com.invoice.controller;

import com.invoice.config.SecurityConfig;
import com.invoice.dto.AdminUserPageResponse;
import com.invoice.dto.AdminUserStats;
import com.invoice.exception.BusinessException;
import com.invoice.exception.GlobalExceptionHandler;
import com.invoice.security.JwtAuthenticationFilter;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserQuotaService;
import com.invoice.service.UserService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@Import({GlobalExceptionHandler.class, UserAdminControllerTest.TestSecurityConfig.class})
class UserAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserQuotaService userQuotaService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void allowRequests() {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
        when(userService.getAdminUsers(anyInt(), anyInt(), any(), any(), any(), anyLong()))
                .thenReturn(new AdminUserPageResponse(
                        List.of(), 0, 1, 10, 0, new AdminUserStats(0, 0, 0, 0)));
    }

    @Test
    void allowsAdministratorToListUsers() throws Exception {
        mockMvc.perform(get("/users/admin").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void deniesRegularUserAndAnonymousRequests() throws Exception {
        mockMvc.perform(get("/users/admin").with(authentication(auth("USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesPaginationAndRoleWhitelist() throws Exception {
        mockMvc.perform(get("/users/admin?page=0&pageSize=101&role=OWNER")
                        .with(authentication(auth("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void validatesCreatePayload() throws Exception {
        mockMvc.perform(post("/users/admin")
                        .with(authentication(auth("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"weak\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void mapsMissingUserAndLastAdminConflict() throws Exception {
        when(userService.updateRole(404L, "USER", 1L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在"));
        when(userService.updateStatus(2L, false, 1L))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, 40903, "系统必须保留至少一个启用的管理员"));

        mockMvc.perform(put("/users/admin/404/role")
                        .with(authentication(auth("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40403));

        mockMvc.perform(put("/users/admin/2/status")
                        .with(authentication(auth("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    @Test
    void returnsTooManyRequestsWithRetryAfter() throws Exception {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(false, 17));

        mockMvc.perform(get("/users/admin").with(authentication(auth("ADMIN"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.code").value(42903));
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "admin", role, 0L);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/users/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.sendError(HttpStatus.UNAUTHORIZED.value()))
                            .accessDeniedHandler((request, response, exception) ->
                                    response.sendError(HttpStatus.FORBIDDEN.value())))
                    .build();
        }
    }
}
