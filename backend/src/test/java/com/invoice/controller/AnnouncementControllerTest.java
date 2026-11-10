package com.invoice.controller;

import com.invoice.config.SecurityConfig;
import com.invoice.dto.AdminAnnouncementPageResponse;
import com.invoice.dto.AdminAnnouncementResponse;
import com.invoice.dto.AnnouncementPageResponse;
import com.invoice.dto.AnnouncementResponse;
import com.invoice.dto.AnnouncementSaveRequest;
import com.invoice.exception.BusinessException;
import com.invoice.exception.GlobalExceptionHandler;
import com.invoice.security.ApiKeyAuthenticationFilter;
import com.invoice.security.JwtAuthenticationFilter;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.AnnouncementService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {AnnouncementAdminController.class, AnnouncementController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}
        )
)
@Import({GlobalExceptionHandler.class, AnnouncementControllerTest.TestSecurityConfig.class})
class AnnouncementControllerTest {

    private static final String VALID_BODY =
            "{\"title\":\"新功能上线\",\"content\":\"## 更新\\n- 支持批量导入\",\"category\":\"FEATURE\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnouncementService announcementService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void allowRequests() {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
        when(announcementService.getAdminPage(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new AdminAnnouncementPageResponse(List.of(), 0, 1, 10, 0,
                        new com.invoice.dto.AnnouncementStats(0, 0, 0, 0)));
        when(announcementService.getPublishedPage(anyInt(), anyInt(), any()))
                .thenReturn(new AnnouncementPageResponse(List.of(), 0, 1, 10, 0));
    }

    // ---- 管理端 ----

    @Test
    void allowsAdministratorToListAnnouncements() throws Exception {
        mockMvc.perform(get("/announcements/admin").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void deniesNonAdministratorsOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/announcements/admin").with(authentication(auth("USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/announcements/admin").with(authentication(auth("INVOICE_CLERK"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/announcements/admin")
                        .with(authentication(auth("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/announcements/admin"))
                .andExpect(status().isUnauthorized());
        verify(announcementService, never()).create(any(), any());
    }

    @Test
    void validatesAdminListFilters() throws Exception {
        mockMvc.perform(get("/announcements/admin?page=0&category=OTHER&status=ARCHIVED")
                        .with(authentication(auth("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createsAnnouncementAsDraft() throws Exception {
        when(announcementService.create(any(AnnouncementSaveRequest.class), eq(1L)))
                .thenReturn(adminResponse("DRAFT", 0));

        mockMvc.perform(post("/announcements/admin")
                        .with(authentication(auth("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void validatesCreatePayload() throws Exception {
        String longTitle = "a".repeat(101);
        mockMvc.perform(post("/announcements/admin")
                        .with(authentication(auth("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"content\":\" \",\"category\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(announcementService, never()).create(any(), any());
    }

    @Test
    void exposesStateTransitions() throws Exception {
        when(announcementService.publish(5L)).thenReturn(adminResponse("PUBLISHED", 1));
        when(announcementService.repush(5L)).thenReturn(adminResponse("PUBLISHED", 2));
        when(announcementService.offline(5L)).thenReturn(adminResponse("OFFLINE", 2));

        mockMvc.perform(put("/announcements/admin/5/publish").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushVersion").value(1));
        mockMvc.perform(post("/announcements/admin/5/repush").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushVersion").value(2));
        mockMvc.perform(put("/announcements/admin/5/offline").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        mockMvc.perform(delete("/announcements/admin/5").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("公告已删除"));
        verify(announcementService).delete(5L);
    }

    @Test
    void mapsIllegalTransitionToConflict() throws Exception {
        when(announcementService.repush(6L))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, 40920, "只有已发布的公告可以重新推送"));

        mockMvc.perform(post("/announcements/admin/6/repush").with(authentication(auth("ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40920));
    }

    // ---- 用户端 ----

    @Test
    void anyLoggedInRoleCanFetchUnreadAnnouncements() throws Exception {
        when(announcementService.getUnread(1L)).thenReturn(List.of(new AnnouncementResponse(
                5L, "新功能上线", "内容", "FEATURE", "PUBLISHED", 1,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())));

        for (String role : List.of("USER", "INVOICE_CLERK", "ADMIN")) {
            mockMvc.perform(get("/announcements/unread").with(authentication(auth(role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(5));
        }
        mockMvc.perform(get("/announcements/unread"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marksAnnouncementsAsRead() throws Exception {
        mockMvc.perform(post("/announcements/read")
                        .with(authentication(auth("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(announcementService).markRead(1L, List.of(5L, 6L));
    }

    @Test
    void validatesMarkReadPayload() throws Exception {
        mockMvc.perform(post("/announcements/read")
                        .with(authentication(auth("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/announcements/read")
                        .with(authentication(auth("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(announcementService, never()).markRead(any(), any());
    }

    @Test
    void listsHistoryForRegularUsers() throws Exception {
        mockMvc.perform(get("/announcements?page=1&pageSize=10&category=FEATURE")
                        .with(authentication(auth("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/announcements?pageSize=51").with(authentication(auth("USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private AdminAnnouncementResponse adminResponse(String status, int pushVersion) {
        return new AdminAnnouncementResponse(5L, "新功能上线", "内容", "FEATURE", status, pushVersion,
                null, LocalDateTime.now(), LocalDateTime.now(), 0L);
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "tester", role, 0L);
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
                            .requestMatchers("/announcements/admin/**").hasRole("ADMIN")
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
