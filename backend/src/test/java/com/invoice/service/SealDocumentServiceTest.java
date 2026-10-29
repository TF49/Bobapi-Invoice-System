package com.invoice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.invoice.dto.SealDocumentResponse;
import com.invoice.entity.SealDocument;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.SealDocumentMapper;
import com.invoice.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SealDocumentServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SealDocument.class);
    }

    @Mock
    private SealDocumentMapper sealDocumentMapper;

    @Mock
    private UserSealQuotaService userSealQuotaService;

    @Mock
    private UserMapper userMapper;

    @TempDir
    Path tempDir;

    private SealDocumentService service;

    @BeforeEach
    void setUp() {
        service = new SealDocumentService(sealDocumentMapper, userSealQuotaService, userMapper, tempDir.toString());
    }

    @Test
    void rejectsEmptyFileOnUpload() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadSourceDocument(2L, emptyFile));
        assertEquals(40001, ex.getCode());
    }

    @Test
    void rejectsDangerousExtensions() {
        MockMultipartFile scriptFile = new MockMultipartFile("file", "run.bat", "application/octet-stream", "echo 1".getBytes());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadSourceDocument(2L, scriptFile));
        assertEquals(40007, ex.getCode());
    }

    @Test
    void uploadsSourceDocumentAndDeductsTenYuan() {
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "dummy pdf content".getBytes()
        );

        when(sealDocumentMapper.insert(any(SealDocument.class))).thenAnswer(invocation -> {
            SealDocument doc = invocation.getArgument(0);
            doc.setId(99L);
            return 1;
        });

        SealDocumentResponse resp = service.uploadSourceDocument(2L, validFile);

        assertNotNull(resp);
        assertEquals("PENDING", resp.getStatus());
        assertEquals("cert.pdf", resp.getSourceFileName());
        assertEquals(new BigDecimal("10.00"), resp.getFeeAmount());

        // 验证原子前置扣除了 10 元额度
        verify(userSealQuotaService).deductQuota(eq(2L), eq(99L), eq(new BigDecimal("10.00")), any());
        verify(sealDocumentMapper).updateById(any(SealDocument.class));
    }

    @Test
    void uploadWithNewIdempotencyKeyStoresKeyAndDeducts() {
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "dummy pdf content".getBytes());
        when(sealDocumentMapper.selectOne(any())).thenReturn(null);
        when(sealDocumentMapper.insert(any(SealDocument.class))).thenAnswer(invocation -> {
            SealDocument doc = invocation.getArgument(0);
            assertEquals("seal-key-0000000001", doc.getIdempotencyKey());
            assertEquals(sha256("dummy pdf content".getBytes()), doc.getSourceFileSha256());
            doc.setId(99L);
            return 1;
        });

        SealDocumentService.UploadResult result = service.uploadSourceDocument(2L, validFile, "seal-key-0000000001");

        assertFalse(result.duplicate());
        assertEquals(99L, result.document().getId());
        verify(userSealQuotaService).deductQuota(eq(2L), eq(99L), eq(new BigDecimal("10.00")), any());
    }

    @Test
    void uploadWithRepeatedIdempotencyKeyReturnsExistingWithoutDeducting() {
        byte[] content = "dummy pdf content".getBytes();
        MockMultipartFile validFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", content);
        SealDocument existing = new SealDocument();
        existing.setId(77L);
        existing.setUserId(2L);
        existing.setSourceFileName("cert.pdf");
        existing.setSourceFileSize((long) content.length);
        existing.setSourceFileSha256(sha256(content));
        existing.setStatus("PENDING");
        existing.setFeeAmount(new BigDecimal("10.00"));
        when(sealDocumentMapper.selectOne(any())).thenReturn(existing);

        SealDocumentService.UploadResult result = service.uploadSourceDocument(2L, validFile, "seal-key-0000000001");

        assertTrue(result.duplicate());
        assertEquals(77L, result.document().getId());
        verify(sealDocumentMapper, never()).insert(any(SealDocument.class));
        verify(userSealQuotaService, never()).deductQuota(any(), any(), any(), any());
    }

    @Test
    void uploadRejectsSameNameAndSizeButDifferentContent() {
        // 两份内容长度相同（17 字节）、文件名相同，只有内容不同
        byte[] original = "dummy pdf content".getBytes();
        byte[] changed = "DUMMY PDF CONTENT".getBytes();
        assertEquals(original.length, changed.length);
        MockMultipartFile changedFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", changed);

        SealDocument existing = new SealDocument();
        existing.setId(77L);
        existing.setUserId(2L);
        existing.setSourceFileName("cert.pdf");
        existing.setSourceFileSize((long) original.length);
        existing.setSourceFileSha256(sha256(original));
        when(sealDocumentMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadSourceDocument(2L, changedFile, "seal-key-0000000001"));

        assertEquals(40903, ex.getCode());
        verify(sealDocumentMapper, never()).insert(any(SealDocument.class));
        verify(userSealQuotaService, never()).deductQuota(any(), any(), any(), any());
    }

    @Test
    void uploadFallsBackToNameAndSizeForLegacyRecordWithoutHash() {
        byte[] content = "dummy pdf content".getBytes();
        MockMultipartFile validFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", content);
        SealDocument legacy = new SealDocument();
        legacy.setId(66L);
        legacy.setUserId(2L);
        legacy.setSourceFileName("cert.pdf");
        legacy.setSourceFileSize((long) content.length);
        legacy.setSourceFileSha256(null);
        legacy.setStatus("PENDING");
        when(sealDocumentMapper.selectOne(any())).thenReturn(legacy);

        SealDocumentService.UploadResult result = service.uploadSourceDocument(2L, validFile, "seal-key-0000000001");

        assertTrue(result.duplicate());
        verify(userSealQuotaService, never()).deductQuota(any(), any(), any(), any());
    }

    @Test
    void webUploadAlsoRecordsContentHash() {
        byte[] content = "dummy pdf content".getBytes();
        MockMultipartFile validFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", content);
        when(sealDocumentMapper.insert(any(SealDocument.class))).thenAnswer(invocation -> {
            SealDocument doc = invocation.getArgument(0);
            assertEquals(sha256(content), doc.getSourceFileSha256());
            assertEquals(null, doc.getIdempotencyKey());
            doc.setId(98L);
            return 1;
        });

        service.uploadSourceDocument(2L, validFile);

        verify(sealDocumentMapper, never()).selectOne(any());
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void uploadRejectsIdempotencyKeyReusedForDifferentFile() {
        MockMultipartFile otherFile = new MockMultipartFile(
                "file", "other.pdf", "application/pdf", "another file".getBytes());
        SealDocument existing = new SealDocument();
        existing.setId(77L);
        existing.setUserId(2L);
        existing.setSourceFileName("cert.pdf");
        existing.setSourceFileSize(17L);
        when(sealDocumentMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadSourceDocument(2L, otherFile, "seal-key-0000000001"));

        assertEquals(40903, ex.getCode());
        verify(userSealQuotaService, never()).deductQuota(any(), any(), any(), any());
    }

    @Test
    void uploadReturnsConflictWhenConcurrentRequestWonTheKey() {
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "dummy pdf content".getBytes());
        when(sealDocumentMapper.selectOne(any())).thenReturn(null);
        when(sealDocumentMapper.insert(any(SealDocument.class)))
                .thenThrow(new DuplicateKeyException("uk_sd_user_idempotency"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadSourceDocument(2L, validFile, "seal-key-0000000001"));

        assertEquals(40904, ex.getCode());
        verify(userSealQuotaService, never()).deductQuota(any(), any(), any(), any());
    }

    @Test
    void cancelDocumentByOwnerRefundsTenYuan() {
        SealDocument doc = new SealDocument();
        doc.setId(101L);
        doc.setUserId(2L);
        doc.setStatus("PENDING");
        doc.setSourceFileName("contract.docx");
        doc.setFeeAmount(new BigDecimal("10.00"));

        when(sealDocumentMapper.selectById(101L)).thenReturn(doc);
        when(sealDocumentMapper.update(any(), any())).thenReturn(1); // CAS 成功

        SealDocumentResponse resp = service.cancelDocument(101L, 2L, false);

        assertEquals("CANCELLED", resp.getStatus());
        // 验证自动退款 10 元
        verify(userSealQuotaService).refundQuota(eq(2L), eq(101L), eq(new BigDecimal("10.00")), any());
    }

    @Test
    void cancelDocumentRejectsNonOwner() {
        SealDocument doc = new SealDocument();
        doc.setId(101L);
        doc.setUserId(2L);
        doc.setStatus("PENDING");

        when(sealDocumentMapper.selectById(101L)).thenReturn(doc);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelDocument(101L, 3L, false)); // user 3 tries to cancel user 2's doc

        assertEquals(40301, ex.getCode());
        verify(userSealQuotaService, never()).refundQuota(any(), any(), any(), any());
    }

    @Test
    void rejectDocumentByAdminRefundsTenYuan() {
        SealDocument doc = new SealDocument();
        doc.setId(102L);
        doc.setUserId(2L);
        doc.setStatus("PENDING");
        doc.setSourceFileName("doc.pdf");
        doc.setFeeAmount(new BigDecimal("10.00"));

        when(sealDocumentMapper.selectById(102L)).thenReturn(doc);
        when(sealDocumentMapper.update(any(), any())).thenReturn(1); // CAS 成功

        SealDocumentResponse resp = service.rejectDocument(102L, 1L, "缺少公章授权书");

        assertEquals("REJECTED", resp.getStatus());
        assertEquals("缺少公章授权书", resp.getRejectReason());
        verify(userSealQuotaService).refundQuota(eq(2L), eq(102L), eq(new BigDecimal("10.00")), any());
    }

    @Test
    void uploadSealedDocumentCompletesTheDocument() {
        SealDocument doc = new SealDocument();
        doc.setId(103L);
        doc.setUserId(2L);
        doc.setStatus("PENDING");
        doc.setSourceFileName("source.pdf");

        MockMultipartFile sealedFile = new MockMultipartFile(
                "file", "sealed_cert.pdf", "application/pdf", "sealed pdf content".getBytes()
        );

        when(sealDocumentMapper.selectById(103L)).thenReturn(doc);
        when(sealDocumentMapper.update(any(), any())).thenReturn(1); // CAS 成功

        SealDocumentResponse resp = service.uploadSealedDocument(103L, sealedFile, 1L);

        assertEquals("COMPLETED", resp.getStatus());
        assertEquals("sealed_cert.pdf", resp.getSealedFileName());
    }
}
