package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.invoice.dto.DashboardStats;
import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.dto.BatchInvoiceResponse;
import com.invoice.dto.InvoiceResponse;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceBatch;
import com.invoice.exception.BatchValidationException;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceBatchMapper;
import com.invoice.mapper.InvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Invoice.class);
    }

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private InvoiceBatchMapper invoiceBatchMapper;

    @TempDir
    Path uploadDirectory;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceMapper, invoiceBatchMapper, uploadDirectory.toString());
        service.initializeUploadDirectory();
    }

    @Test
    void returnsExistingInvoiceForTheSameIdempotentRequest() {
        Invoice existing = invoice(1L, 8L, "PENDING");
        existing.setCompanyName("示例公司");
        existing.setTaxNumber("ABCDEFGHIJKLMNO");
        existing.setAmount(new BigDecimal("100.00"));
        existing.setIdempotencyKey("12345678-1234-1234-1234-123456789012");
        when(invoiceMapper.selectOne(any())).thenReturn(existing);

        InvoiceResponse response = service.createInvoice(
                8L,
                existing.getIdempotencyKey(),
                "示例公司",
                "abcdefghijklmno",
                new BigDecimal("100.0")
        );

        assertThat(response.id()).isEqualTo(1L);
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentData() {
        Invoice existing = invoice(1L, 8L, "PENDING");
        existing.setCompanyName("原公司");
        existing.setTaxNumber("ABCDEFGHIJKLMNO");
        existing.setAmount(new BigDecimal("100.00"));
        when(invoiceMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.createInvoice(
                8L, "12345678-1234-1234-1234-123456789012",
                "另一家公司", "ABCDEFGHIJKLMNO", new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40902);
    }

    @Test
    void rejectsDuplicateNormalizedBatchRowsWithTheOriginalRowNumber() {
        List<BatchInvoiceItemRequest> items = List.of(
                batchItem(2, "示例公司", "abcde12345678901", "100"),
                batchItem(5, " 示例公司 ", "ABCDE12345678901", "100.00")
        );

        assertThatThrownBy(() -> service.createInvoicesBatch(
                8L, "batch-1234567890123456", items))
                .isInstanceOfSatisfying(BatchValidationException.class, exception ->
                        assertThat(exception.getErrors()).singleElement().satisfies(error -> {
                            assertThat(error.rowNumber()).isEqualTo(5);
                            assertThat(error.field()).isEqualTo("row");
                            assertThat(error.code()).isEqualTo(42202);
                        }));

        verify(invoiceBatchMapper, never()).insert(any(InvoiceBatch.class));
        verify(invoiceMapper, never()).insertBatch(anyList());
    }

    @Test
    void reportsInvalidDecimalTextAsAStructuredRowError() {
        List<BatchInvoiceItemRequest> items = List.of(
                batchItem(3, "示例公司", "ABCDE12345678901", "1e3"),
                batchItem(6, "示例公司", "ABCDE12345678902", "100abc")
        );

        assertThatThrownBy(() -> service.createInvoicesBatch(
                8L, "batch-1234567890123456", items))
                .isInstanceOfSatisfying(BatchValidationException.class, exception -> {
                    assertThat(exception.getErrors()).extracting("rowNumber", "field", "code")
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple(3, "amount", 42202),
                                    org.assertj.core.groups.Tuple.tuple(6, "amount", 42202));
                    assertThat(exception.getErrors()).allMatch(error ->
                            error.message().contains("格式不正确"));
                });
    }

    @Test
    void createsAWholeBatchWithNormalizedValuesAndReturnsInvoiceIds() {
        doAnswer(invocation -> {
            InvoiceBatch batch = invocation.getArgument(0);
            batch.setId(50L);
            return 1;
        }).when(invoiceBatchMapper).insert(any(InvoiceBatch.class));
        when(invoiceMapper.insertBatch(anyList())).thenReturn(2);

        Invoice first = batchInvoice(101L, 50L, 2, "示例公司 A", "ABCDE12345678901", "100.00");
        Invoice second = batchInvoice(102L, 50L, 4, "示例公司 B", "ABCDE12345678902", "20.50");
        when(invoiceMapper.selectByBatchId(50L)).thenReturn(List.of(first, second));

        BatchInvoiceResponse response = service.createInvoicesBatch(
                8L,
                "batch-1234567890123456",
                List.of(
                        batchItem(2, " 示例公司 A ", "abcde12345678901", "100"),
                        batchItem(4, "示例公司 B", "ABCDE12345678902", "20.5")
                ));

        assertThat(response.getBatchId()).isEqualTo(50L);
        assertThat(response.getTotalAmount()).isEqualTo("120.50");
        assertThat(response.getItems()).extracting("rowNumber", "invoiceId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, 101L),
                        org.assertj.core.groups.Tuple.tuple(4, 102L));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Invoice>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(invoiceMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).first().satisfies(invoice -> {
            assertThat(invoice.getCompanyName()).isEqualTo("示例公司 A");
            assertThat(invoice.getTaxNumber()).isEqualTo("ABCDE12345678901");
            assertThat(invoice.getAmount()).isEqualByComparingTo("100.00");
            assertThat(invoice.getStatus()).isEqualTo("PENDING");
            assertThat(invoice.getIdempotencyKey()).isNull();
        });
    }

    @Test
    void validatesFileSignatureAndCompletesAPendingInvoice() throws Exception {
        Invoice pending = invoice(2L, 8L, "PENDING");
        Invoice completed = invoice(2L, 8L, "COMPLETED");
        completed.setFileName("发票.png");
        when(invoiceMapper.selectById(2L)).thenReturn(pending).thenAnswer(ignored -> {
            try (var files = Files.list(uploadDirectory)) {
                completed.setFilePath(files.findFirst().orElseThrow().getFileName().toString());
            }
            return completed;
        });
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "发票.png", "image/png", imageBytes("png", 2, 2));

        InvoiceResponse response = service.uploadInvoiceFile(2L, file);

        assertThat(response.downloadable()).isTrue();
        try (var files = Files.list(uploadDirectory)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void assemblesDashboardStatsFromTheThreeAggregateQueries() {
        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                3L, 1L, 2L, new BigDecimal("2300.75")));
        when(invoiceMapper.selectUserInvoiceStats()).thenReturn(List.of(
                new InvoiceMapper.UserInvoiceStat(
                        8L, "user", 2L, 1L, new BigDecimal("2300.75"))));
        when(invoiceMapper.selectAllTimelineStats()).thenReturn(List.of(
                new InvoiceMapper.TimelineStatWithUser(
                        8L, LocalDate.of(2026, 8, 19), 2L, new BigDecimal("2300.75"))));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.totalInvoices()).isEqualTo(3L);
        assertThat(stats.pendingInvoices()).isEqualTo(1L);
        assertThat(stats.completedInvoices()).isEqualTo(2L);
        assertThat(stats.totalAmount()).isEqualByComparingTo("2300.75");
        assertThat(stats.userStats()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("user");
            assertThat(user.timeline()).singleElement().satisfies(point -> {
                assertThat(point.date()).isEqualTo(LocalDate.of(2026, 8, 19));
                assertThat(point.count()).isEqualTo(2L);
            });
        });
    }

    @Test
    void rejectsAFileWhoseContentDoesNotMatchItsDeclaredType() {
        when(invoiceMapper.selectById(3L)).thenReturn(invoice(3L, 8L, "PENDING"));
        // 声称是 PNG但内容不匹配
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes());

        assertThatThrownBy(() -> service.uploadInvoiceFile(3L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40003);
        verify(invoiceMapper, never()).update(
                isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any());
    }

    @Test
    void rejectsACorruptedImageWithAValidSignature() {
        when(invoiceMapper.selectById(6L)).thenReturn(invoice(6L, 8L, "PENDING"));
        byte[] corruptedPng = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.png", "image/png", corruptedPng);

        assertThatThrownBy(() -> service.uploadInvoiceFile(6L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40003);
        verify(invoiceMapper, never()).update(
                isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any());
    }

    @Test
    void rejectsAnImageThatExceedsConfiguredDimensions() throws Exception {
        when(invoiceMapper.selectById(7L)).thenReturn(invoice(7L, 8L, "PENDING"));
        ReflectionTestUtils.setField(service, "maxImageWidth", 1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", imageBytes("png", 2, 2));

        assertThatThrownBy(() -> service.uploadInvoiceFile(7L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(41301);
    }

    @Test
    void preventsAnotherUserFromDownloadingTheInvoice() throws Exception {
        Invoice completed = invoice(4L, 8L, "COMPLETED");
        completed.setFilePath("stored.png");
        completed.setFileName("发票.png");
        Files.write(uploadDirectory.resolve("stored.png"), imageBytes("png", 2, 2));
        when(invoiceMapper.selectById(4L)).thenReturn(completed);

        assertThatThrownBy(() -> service.downloadInvoiceFile(4L, 9L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40301);

        InvoiceService.InvoiceDownload adminDownload = service.downloadInvoiceFile(4L, 9L, true);
        assertThat(adminDownload.fileName()).isEqualTo("发票.png");
        assertThat(adminDownload.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsPdfUploadWithBadRequest() {
        when(invoiceMapper.selectById(5L)).thenReturn(invoice(5L, 8L, "PENDING"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "发票.pdf", "application/pdf", "%PDF-1.7 demo".getBytes());

        assertThatThrownBy(() -> service.uploadInvoiceFile(5L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40003);
    }

    @Test
    void marksMissingAndUnsupportedFilesAsUnavailable() throws Exception {
        Invoice missing = invoice(8L, 8L, "COMPLETED");
        missing.setFilePath("missing.png");
        missing.setFileName("missing.png");

        Invoice legacyPdf = invoice(9L, 8L, "COMPLETED");
        legacyPdf.setFilePath("legacy.pdf");
        legacyPdf.setFileName("legacy.pdf");
        Files.writeString(uploadDirectory.resolve("legacy.pdf"), "%PDF-1.7");
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any()))
                .thenReturn(List.of(missing, legacyPdf));

        List<InvoiceResponse> responses = service.getInvoicesByUserId(8L);

        assertThat(responses.get(0).fileExists()).isFalse();
        assertThat(responses.get(0).downloadable()).isFalse();
        assertThat(responses.get(1).fileExists()).isTrue();
        assertThat(responses.get(1).downloadable()).isFalse();
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    private Invoice invoice(Long id, Long userId, String status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setUserId(userId);
        invoice.setStatus(status);
        invoice.setCompanyName("示例公司");
        invoice.setTaxNumber("ABCDEFGHIJKLMNO");
        invoice.setAmount(new BigDecimal("100.00"));
        return invoice;
    }

    private BatchInvoiceItemRequest batchItem(int rowNumber, String companyName,
                                              String taxNumber, String amount) {
        BatchInvoiceItemRequest item = new BatchInvoiceItemRequest();
        item.setRowNumber(rowNumber);
        item.setCompanyName(companyName);
        item.setTaxNumber(taxNumber);
        item.setAmount(amount);
        return item;
    }

    private Invoice batchInvoice(Long id, Long batchId, int rowNumber, String companyName,
                                 String taxNumber, String amount) {
        Invoice invoice = invoice(id, 8L, "PENDING");
        invoice.setBatchId(batchId);
        invoice.setBatchRowNumber(rowNumber);
        invoice.setCompanyName(companyName);
        invoice.setTaxNumber(taxNumber);
        invoice.setAmount(new BigDecimal(amount));
        return invoice;
    }
}
