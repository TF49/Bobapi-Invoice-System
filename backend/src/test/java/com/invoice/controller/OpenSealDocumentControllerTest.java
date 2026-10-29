package com.invoice.controller;

import com.invoice.config.SecurityConfig;
import com.invoice.dto.SealDocumentResponse;
import com.invoice.exception.BusinessException;
import com.invoice.exception.GlobalExceptionHandler;
import com.invoice.security.ApiKeyAuthenticationFilter;
import com.invoice.security.JwtAuthenticationFilter;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.SealDocumentService;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OpenSealDocumentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}
        )
)
@Import({GlobalExceptionHandler.class, OpenSealDocumentControllerTest.TestSecurityConfig.class})
class OpenSealDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SealDocumentService sealDocumentService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(10L, "potato_client", role, 1L);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private SealDocumentResponse doc(String status) {
        SealDocumentResponse doc = new SealDocumentResponse();
        doc.setId(501L);
        doc.setUserId(10L);
        doc.setUsername("potato_client");
        doc.setSourceFileName("contract.pdf");
        doc.setSourceFileSize(1024L);
        doc.setSourceFileExt("pdf");
        doc.setFeeAmount(new BigDecimal("10.00"));
        doc.setStatus(status);
        doc.setOperatorId(2L);
        doc.setOperatorName("clerk_a");
        doc.setCreatedAt(LocalDateTime.now());
        if ("COMPLETED".equals(status)) {
            doc.setSealedFileName("contract_sealed.pdf");
            doc.setSealedFileSize(2048L);
            doc.setCompletedAt(LocalDateTime.now());
        }
        return doc;
    }

    private static final String IDEMPOTENCY_KEY = "seal-upload-20261029-0001";

    @Test
    void uploadsSourceDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());
        when(sealDocumentService.uploadSourceDocument(eq(10L), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new SealDocumentService.UploadResult(doc("PENDING"), false));

        mockMvc.perform(multipart("/open/v1/documents").file(file)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .with(authentication(auth("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件上传成功，等待盖章处理"))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.downloadable").value(false))
                // 不暴露内部用户和处理人信息
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.operatorId").doesNotExist())
                .andExpect(jsonPath("$.data.operatorName").doesNotExist());
    }

    @Test
    void returnsExistingRecordOnDuplicateUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());
        when(sealDocumentService.uploadSourceDocument(eq(10L), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new SealDocumentService.UploadResult(doc("PENDING"), true));

        mockMvc.perform(multipart("/open/v1/documents").file(file)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .with(authentication(auth("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.message").value("重复请求，已返回首次上传的记录，未重复扣费"));
    }

    @Test
    void rejectsUploadWithoutIdempotencyKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/open/v1/documents").file(file)
                        .with(authentication(auth("USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        verify(sealDocumentService, never()).uploadSourceDocument(any(), any(), any());
    }

    @Test
    void rejectsUploadWithMalformedIdempotencyKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/open/v1/documents").file(file)
                        .header("Idempotency-Key", "short")
                        .with(authentication(auth("USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        verify(sealDocumentService, never()).uploadSourceDocument(any(), any(), any());
    }

    @Test
    void hidesSealQuotaBalanceWhenInsufficient() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());
        when(sealDocumentService.uploadSourceDocument(eq(10L), any(), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 40002,
                        "盖章额度不足，本次盖章需 10 元，当前可用余额 3 元，请先充值"));

        mockMvc.perform(multipart("/open/v1/documents").file(file)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .with(authentication(auth("USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("盖章额度不足，请先充值"));
    }

    @Test
    void listsMyDocumentsWithStatusFilter() throws Exception {
        when(sealDocumentService.getMyDocuments(10L, "COMPLETED")).thenReturn(List.of(doc("COMPLETED")));

        mockMvc.perform(get("/open/v1/documents").param("status", "COMPLETED")
                        .with(authentication(auth("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].downloadable").value(true))
                .andExpect(jsonPath("$.data[0].sealedFileName").value("contract_sealed.pdf"));
    }

    @Test
    void listsAllMyDocumentsWithoutFilter() throws Exception {
        when(sealDocumentService.getMyDocuments(eq(10L), isNull())).thenReturn(List.of(doc("PENDING")));

        mockMvc.perform(get("/open/v1/documents").with(authentication(auth("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void adminApiKeyCanOnlyCancelOwnDocument() throws Exception {
        when(sealDocumentService.cancelDocument(501L, 10L, false)).thenReturn(doc("CANCELLED"));

        mockMvc.perform(post("/open/v1/documents/501/cancel")
                        .with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 即使是管理员账号，OpenAPI 也按普通用户身份撤销
        verify(sealDocumentService).cancelDocument(501L, 10L, false);
    }

    @Test
    void downloadsSealedFile() throws Exception {
        byte[] bytes = "%PDF-1.4 sealed".getBytes();
        when(sealDocumentService.downloadSealedFile(501L, 10L, false))
                .thenReturn(new SealDocumentService.FileDownload(
                        new ByteArrayResource(bytes), "contract_sealed.pdf", "application/pdf", bytes.length));

        mockMvc.perform(get("/open/v1/documents/501/download")
                        .with(authentication(auth("INVOICE_CLERK"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(bytes));

        verify(sealDocumentService).downloadSealedFile(501L, 10L, false);
    }

    @Test
    void doesNotExposeSealQuotaEndpoint() throws Exception {
        mockMvc.perform(get("/open/v1/documents/quota").with(authentication(auth("USER"))))
                .andExpect(result -> {
                    int code = result.getResponse().getStatus();
                    if (code == 200) {
                        throw new AssertionError("/open/v1/documents/quota 不应返回 200");
                    }
                });
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
