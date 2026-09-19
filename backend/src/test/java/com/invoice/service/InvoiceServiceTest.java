package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.invoice.dto.DashboardStats;
import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.dto.BatchInvoiceResponse;
import com.invoice.dto.InvoiceResponse;
import com.invoice.dto.OpenInvoiceResponse;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceBatch;
import com.invoice.exception.BatchValidationException;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceBatchMapper;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.RechargeRequestMapper;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserQuotaMapper;
import com.invoice.service.UserQuotaService;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private UserQuotaService userQuotaService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserQuotaMapper userQuotaMapper;

    @Mock
    private RechargeRequestMapper rechargeRequestMapper;

    @Mock
    private SupplierSettlementService supplierSettlementService;

    @TempDir
    Path uploadDirectory;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceMapper, invoiceBatchMapper, userQuotaService, userMapper, userQuotaMapper, rechargeRequestMapper, supplierSettlementService, uploadDirectory.toString());
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
                new BigDecimal("100.0"),
                InvoiceService.FIXED_INVOICE_TYPE,
                null
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
                "另一家公司", "ABCDEFGHIJKLMNO", new BigDecimal("100.00"),
                InvoiceService.FIXED_INVOICE_TYPE, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40902);
    }

    @Test
    void rejectsNonFixedInvoiceType() {
        assertThatThrownBy(() -> service.createInvoice(
                8L, "12345678-1234-1234-1234-123456789012",
                "示例公司", "ABCDEFGHIJKLMNO", new BigDecimal("100.00"), "咨询服务费", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开票类型必须为技术服务费、AI订阅服务费、计算服务费或研发和技术服务");
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    void createsInvoiceWithAiSubscriptionServiceType() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(99L);
            return 1;
        });

        InvoiceResponse response = service.createInvoice(
                8L, "12345678-1234-1234-1234-123456789099",
                "示例AI公司", "ABCDEFGHIJKLMNO", new BigDecimal("200.00"),
                "AI订阅服务费", "AI充值"
        );

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.invoiceType()).isEqualTo("AI订阅服务费");
        assertThat(response.submissionType()).isEqualTo("MANUAL");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("200.00"), 99L);
    }

    @Test
    void marksOpenApiInvoiceAsApi() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(150L);
            return 1;
        });

        OpenInvoiceResponse response = service.createOpenInvoice(
                8L, "OUT-150", null, "API公司", "ABCDEFGHIJKLMNO",
                new BigDecimal("150.00"), InvoiceService.FIXED_INVOICE_TYPE, "API申请");

        assertThat(response.submissionType()).isEqualTo("API");
        org.mockito.ArgumentCaptor<Invoice> captor = org.mockito.ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubmissionType()).isEqualTo("API");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("150.00"), 150L);
    }

    @Test
    void createsInvoiceWithComputeServiceType() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(100L);
            return 1;
        });

        InvoiceResponse response = service.createInvoice(
                8L, "12345678-1234-1234-1234-123456789100",
                "示例算力公司", "ABCDEFGHIJKLMNO", new BigDecimal("300.00"),
                "计算服务费", "算力服务"
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.invoiceType()).isEqualTo("计算服务费");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("300.00"), 100L);
    }

    @Test
    void createsInvoiceWithResearchAndTechnicalServiceType() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(101L);
            return 1;
        });

        InvoiceResponse response = service.createInvoice(
                8L, "12345678-1234-1234-1234-123456789101",
                "示例研发公司", "ABCDEFGHIJKLMNO", new BigDecimal("400.00"),
                "研发和技术服务", "研发服务"
        );

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.invoiceType()).isEqualTo("研发和技术服务");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("400.00"), 101L);
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
        verify(userQuotaService).deductBatchQuota(8L, new BigDecimal("120.50"), 50L);
        assertThat(captor.getValue()).first().satisfies(invoice -> {
            assertThat(invoice.getCompanyName()).isEqualTo("示例公司 A");
            assertThat(invoice.getTaxNumber()).isEqualTo("ABCDE12345678901");
            assertThat(invoice.getAmount()).isEqualByComparingTo("100.00");
            assertThat(invoice.getStatus()).isEqualTo("PENDING");
            assertThat(invoice.getSubmissionType()).isEqualTo("MANUAL");
            assertThat(invoice.getIdempotencyKey()).isNull();
        });
    }

    @Test
    void marksOpenApiBatchInvoicesAsApi() {
        doAnswer(invocation -> {
            InvoiceBatch batch = invocation.getArgument(0);
            batch.setId(51L);
            return 1;
        }).when(invoiceBatchMapper).insert(any(InvoiceBatch.class));
        when(invoiceMapper.insertBatch(anyList())).thenReturn(1);
        Invoice apiInvoice = batchInvoice(103L, 51L, 2, "API公司", "ABCDE12345678903", "50.00");
        when(invoiceMapper.selectByBatchId(51L)).thenReturn(List.of(apiInvoice));

        service.createInvoicesBatch(
                8L,
                "open_batch_1234567890123456",
                List.of(batchItem(2, "API公司", "ABCDE12345678903", "50.00")),
                "API");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Invoice>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(invoiceMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(invoice ->
                assertThat(invoice.getSubmissionType()).isEqualTo("API"));
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
                3L, 1L, 2L, new BigDecimal("2300.75"), new BigDecimal("500.00")));
        when(invoiceMapper.selectUserInvoiceStats()).thenReturn(List.of(
                new InvoiceMapper.UserInvoiceStat(
                        8L, "user", 2L, 1L, new BigDecimal("2300.75"))));
        when(invoiceMapper.selectAllTimelineStats()).thenReturn(List.of(
                new InvoiceMapper.TimelineStatWithUser(
                        8L, LocalDate.of(2026, 8, 19), 2L, new BigDecimal("2300.75"))));
        when(invoiceMapper.selectInvoiceTypeStats()).thenReturn(List.of(
                new InvoiceMapper.InvoiceTypeStat("技术服务费", 2L, new BigDecimal("2000.00"))));
        when(invoiceMapper.selectTopCompanyStats()).thenReturn(List.of(
                new InvoiceMapper.CompanyStat("示例科技公司", 2L, new BigDecimal("2000.00"))));
        when(invoiceMapper.selectHourDistributionStats()).thenReturn(List.of(
                new InvoiceMapper.HourStat(10, 3L)));
        when(invoiceMapper.selectDailyTrendStats()).thenReturn(List.of(
                new InvoiceMapper.DailyTrendStat(LocalDate.of(2026, 8, 19), 2L, new BigDecimal("2300.75"), 3L, 1L, 0L)));
        when(invoiceMapper.selectAmountRangeSummary()).thenReturn(
                new InvoiceMapper.AmountRangeSummary(
                        1L, new BigDecimal("300.00"),
                        2L, new BigDecimal("2000.75"),
                        0L, BigDecimal.ZERO,
                        0L, BigDecimal.ZERO,
                        0L, BigDecimal.ZERO));
        when(userQuotaMapper.selectQuotaPoolSummary()).thenReturn(
                new UserQuotaMapper.QuotaPoolSummary(new BigDecimal("10000.00"), new BigDecimal("15000.00"), new BigDecimal("5000.00")));
        when(rechargeRequestMapper.countPendingRequests()).thenReturn(1L);
        when(supplierSettlementService.getTotalSettledAmount()).thenReturn(new BigDecimal("500.00"));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.totalInvoices()).isEqualTo(3L);
        assertThat(stats.pendingInvoices()).isEqualTo(1L);
        assertThat(stats.completedInvoices()).isEqualTo(2L);
        assertThat(stats.totalAmount()).isEqualByComparingTo("2300.75");
        assertThat(stats.pendingAmount()).isEqualByComparingTo("500.00");
        assertThat(stats.totalSettledAmount()).isEqualByComparingTo("500.00");
        assertThat(stats.unsettledAmount()).isEqualByComparingTo("1800.75");
        assertThat(stats.userStats()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("user");
            assertThat(user.timeline()).singleElement().satisfies(point -> {
                assertThat(point.date()).isEqualTo(LocalDate.of(2026, 8, 19));
                assertThat(point.count()).isEqualTo(2L);
            });
        });
        assertThat(stats.typeStats()).singleElement().satisfies(t -> {
            assertThat(t.invoiceType()).isEqualTo("技术服务费");
            assertThat(t.count()).isEqualTo(2L);
        });
        assertThat(stats.companyTopStats()).singleElement().satisfies(c -> {
            assertThat(c.companyName()).isEqualTo("示例科技公司");
        });
        assertThat(stats.hourDistribution()).hasSize(24);
        assertThat(stats.amountRangeStats()).hasSize(5);
        assertThat(stats.amountRangeStats().get(0).count()).isEqualTo(1L);
        assertThat(stats.amountRangeStats().get(0).amount()).isEqualByComparingTo("300.00");
        assertThat(stats.amountRangeStats().get(1).count()).isEqualTo(2L);
        assertThat(stats.amountRangeStats().get(1).amount()).isEqualByComparingTo("2000.75");
        assertThat(stats.quotaPoolStats().pendingRechargeCount()).isEqualTo(1L);
        assertThat(stats.quotaPoolStats().totalBalance()).isEqualByComparingTo("10000.00");
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
        ReflectionTestUtils.setField(java.util.Objects.requireNonNull(service), "maxImageWidth", 1);
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

    @Test
    void adminUpdateInvoiceTriggersQuotaAdjustmentWhenAmountChanges() {
        Invoice existing = invoice(14L, 8L, "PENDING");
        existing.setAmount(new BigDecimal("100.00"));
        when(invoiceMapper.selectById(14L)).thenReturn(existing);

        service.adminUpdateInvoice(
                14L, "新示例公司", "ABCDE12345678901",
                new BigDecimal("200.00"), InvoiceService.FIXED_INVOICE_TYPE, "新备注", 1L);

        verify(userQuotaService).adjustQuotaForInvoiceAmountChange(
                8L, new BigDecimal("100.00"), 14L, 1L);
        verify(invoiceMapper).update(isNull(), any());
    }

    @Test
    void updatesProcessedStatusSuccessfullyForOwner() {
        Invoice invoice = invoice(15L, 8L, "COMPLETED");
        invoice.setIsProcessed(false);
        when(invoiceMapper.selectById(15L)).thenReturn(invoice);

        InvoiceResponse response = service.updateInvoiceProcessed(15L, 8L, false, true);

        assertThat(response.id()).isEqualTo(15L);
        verify(invoiceMapper).update(isNull(), any());
    }

    @Test
    void updatesProcessedStatusSuccessfullyForAdmin() {
        Invoice invoice = invoice(16L, 8L, "COMPLETED");
        invoice.setIsProcessed(false);
        when(invoiceMapper.selectById(16L)).thenReturn(invoice);

        InvoiceResponse response = service.updateInvoiceProcessed(16L, 1L, true, true);

        assertThat(response.id()).isEqualTo(16L);
        verify(invoiceMapper).update(isNull(), any());
    }

    @Test
    void throwsForbiddenWhenNonOwnerUpdatesProcessedStatus() {
        Invoice invoice = invoice(17L, 8L, "COMPLETED");
        when(invoiceMapper.selectById(17L)).thenReturn(invoice);

        assertThatThrownBy(() -> service.updateInvoiceProcessed(17L, 99L, false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40301);
        verify(invoiceMapper, never()).update(isNull(), any());
    }

    @Test
    void throwsUnprocessableWhenInvoiceIsNotCompleted() {
        Invoice invoice = invoice(18L, 8L, "PENDING");
        when(invoiceMapper.selectById(18L)).thenReturn(invoice);

        assertThatThrownBy(() -> service.updateInvoiceProcessed(18L, 8L, false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42201);
        verify(invoiceMapper, never()).update(isNull(), any());
    }

    @Test
    void batchUpdatesInvoiceProcessedForOwner() {
        when(invoiceMapper.update(isNull(), any())).thenReturn(2);

        int updatedCount = service.batchUpdateInvoiceProcessed(List.of(10L, 11L), 8L, false, true);

        assertThat(updatedCount).isEqualTo(2);
        verify(invoiceMapper).update(isNull(), any());
    }

    @Test
    void batchUpdatesInvoiceProcessedReturnsZeroForEmptyList() {
        int updatedCount = service.batchUpdateInvoiceProcessed(List.of(), 8L, false, true);

        assertThat(updatedCount).isEqualTo(0);
        verify(invoiceMapper, never()).update(isNull(), any());
    }

    @Test
    void allowsCreatingInvoiceWithNullOrCustomTaxNumber() {
        when(invoiceMapper.selectOne(any())).thenReturn(null);
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(100L);
            return 1;
        });

        InvoiceResponse response = service.createInvoice(
                8L,
                "idemp-custom-tax-1",
                "张三（个人）",
                null,
                new BigDecimal("50.00"),
                "技术服务费",
                "个人申请发票"
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.taxNumber()).isNull();
        assertThat(response.companyName()).isEqualTo("张三（个人）");
    }

    @Test
    void rejectsInvoiceWithTaxNumberExceeding100Chars() {
        assertThatThrownBy(() -> service.createInvoice(
                8L,
                "idemp-tax-too-long",
                "某公司",
                "A".repeat(101),
                new BigDecimal("50.00"),
                "技术服务费",
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("税号不能超过 100 个字符");
    }

    @Test
    void cancelInvoice_byOwner_success() {
        Long invoiceId = 100L;
        Long userId = 8L;
        Invoice pendingInvoice = invoice(invoiceId, userId, "PENDING");
        pendingInvoice.setAmount(new BigDecimal("420.00"));
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenAnswer(invocation -> {
            pendingInvoice.setStatus("CANCELLED");
            return 1;
        });

        InvoiceResponse response = service.cancelInvoice(invoiceId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(userQuotaService).refundQuota(userId, new BigDecimal("420.00"), invoiceId, "取消发票申请");
    }

    @Test
    void cancelInvoice_byAdmin_success() {
        Long invoiceId = 101L;
        Long ownerId = 8L;
        Long adminId = 1L;
        Invoice pendingInvoice = invoice(invoiceId, ownerId, "PENDING");
        pendingInvoice.setAmount(new BigDecimal("500.00"));
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenAnswer(invocation -> {
            pendingInvoice.setStatus("CANCELLED");
            return 1;
        });

        InvoiceResponse response = service.cancelInvoice(invoiceId, adminId, true);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(userQuotaService).refundQuota(ownerId, new BigDecimal("500.00"), invoiceId, "取消发票申请");
    }

    @Test
    void cancelInvoice_byOtherUser_forbidden() {
        Long invoiceId = 102L;
        Long ownerId = 8L;
        Long otherUserId = 9L;
        Invoice pendingInvoice = invoice(invoiceId, ownerId, "PENDING");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);

        assertThatThrownBy(() -> service.cancelInvoice(invoiceId, otherUserId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权取消其他用户的发票");
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any());
    }

    @Test
    void cancelInvoice_completedInvoice_throwsException() {
        Long invoiceId = 103L;
        Long userId = 8L;
        Invoice completedInvoice = invoice(invoiceId, userId, "COMPLETED");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);

        assertThatThrownBy(() -> service.cancelInvoice(invoiceId, userId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能取消待开票的发票申请");
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any());
    }

    @Test
    void cancelInvoice_alreadyCancelled_throwsException() {
        Long invoiceId = 104L;
        Long userId = 8L;
        Invoice cancelledInvoice = invoice(invoiceId, userId, "CANCELLED");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(cancelledInvoice);

        assertThatThrownBy(() -> service.cancelInvoice(invoiceId, userId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能取消待开票的发票申请");
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any());
    }

    @Test
    void cancelInvoice_optimisticLockConflict_throwsException() {
        Long invoiceId = 105L;
        Long userId = 8L;
        Invoice pendingInvoice = invoice(invoiceId, userId, "PENDING");
        pendingInvoice.setAmount(new BigDecimal("100.00"));
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancelInvoice(invoiceId, userId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该发票已被处理，无法取消");
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any());
    }

    @Test
    void applyRedFlush_success() {
        Long invoiceId = 201L;
        Long userId = 8L;
        Invoice completedInvoice = invoice(invoiceId, userId, "COMPLETED");
        completedInvoice.setRedFlushStatus("NONE");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        InvoiceResponse response = service.applyRedFlush(invoiceId, userId, "税号填错了");
        assertThat(response).isNotNull();
        verify(invoiceMapper).update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any());
    }

    @Test
    void applyRedFlush_forbiddenForDifferentUser() {
        Long invoiceId = 202L;
        Invoice completedInvoice = invoice(invoiceId, 8L, "COMPLETED");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);

        assertThatThrownBy(() -> service.applyRedFlush(invoiceId, 999L, "税号填错了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权申请其他用户的发票红冲");
    }

    @Test
    void applyRedFlush_rejectsWhenNotCompleted() {
        Long invoiceId = 203L;
        Long userId = 8L;
        Invoice pendingInvoice = invoice(invoiceId, userId, "PENDING");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);

        assertThatThrownBy(() -> service.applyRedFlush(invoiceId, userId, "申请红冲"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有已开票的发票可以申请红冲");
    }

    @Test
    void applyRedFlush_rejectsDuplicatePending() {
        Long invoiceId = 204L;
        Long userId = 8L;
        Invoice pendingInvoice = invoice(invoiceId, userId, "COMPLETED");
        pendingInvoice.setRedFlushStatus("PENDING");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(pendingInvoice);

        assertThatThrownBy(() -> service.applyRedFlush(invoiceId, userId, "重复申请"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该发票已有待处理的红冲申请，请勿重复提交");
    }

    @Test
    void confirmRedFlush_successAndRefundsQuota() {
        Long invoiceId = 205L;
        Long operatorId = 1L;
        Invoice completedInvoice = invoice(invoiceId, 8L, "COMPLETED");
        completedInvoice.setRedFlushStatus("PENDING");
        completedInvoice.setAmount(new BigDecimal("150.00"));
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        InvoiceResponse response = service.confirmRedFlush(invoiceId, operatorId, "已作废处理");
        assertThat(response).isNotNull();
        verify(userQuotaService).refundQuota(eq(8L), eq(new BigDecimal("150.00")), eq(invoiceId), org.mockito.ArgumentMatchers.contains("发票红冲退还额度"), eq(operatorId), eq("ADMIN"));
    }

    @Test
    void directRedFlush_successWithoutUserApply() {
        Long invoiceId = 208L;
        Long operatorId = 1L;
        Invoice completedInvoice = invoice(invoiceId, 8L, "COMPLETED");
        completedInvoice.setRedFlushStatus("NONE");
        completedInvoice.setAmount(new BigDecimal("200.00"));
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        InvoiceResponse response = service.confirmRedFlush(invoiceId, operatorId, "开票员直接作废重开");
        assertThat(response).isNotNull();
        verify(userQuotaService).refundQuota(eq(8L), eq(new BigDecimal("200.00")), eq(invoiceId), org.mockito.ArgumentMatchers.contains("发票红冲退还额度"), eq(operatorId), eq("ADMIN"));
    }

    @Test
    void adminUpdateInvoice_rejectsPendingOrCompletedRedFlush() {
        Invoice pendingFlush = invoice(301L, 8L, "COMPLETED");
        pendingFlush.setRedFlushStatus("PENDING");
        when(invoiceMapper.selectById(301L)).thenReturn(pendingFlush);

        assertThatThrownBy(() -> service.adminUpdateInvoice(
                301L, "新示例公司", "ABCDE12345678901",
                new BigDecimal("100.00"), InvoiceService.FIXED_INVOICE_TYPE, "新备注", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42201);

        Invoice completedFlush = invoice(302L, 8L, "COMPLETED");
        completedFlush.setRedFlushStatus("COMPLETED");
        when(invoiceMapper.selectById(302L)).thenReturn(completedFlush);

        assertThatThrownBy(() -> service.adminUpdateInvoice(
                302L, "新示例公司", "ABCDE12345678901",
                new BigDecimal("100.00"), InvoiceService.FIXED_INVOICE_TYPE, "新备注", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(42201);
    }

    @Test
    void rejectRedFlush_successAndRecordsReason() {
        Long invoiceId = 206L;
        Long operatorId = 1L;
        Invoice completedInvoice = invoice(invoiceId, 8L, "COMPLETED");
        completedInvoice.setRedFlushStatus("PENDING");
        when(invoiceMapper.selectById(invoiceId)).thenReturn(completedInvoice);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        InvoiceResponse response = service.rejectRedFlush(invoiceId, operatorId, "发票已入账不可红冲");
        assertThat(response).isNotNull();
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any());
        verify(userQuotaService, never()).refundQuota(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getPendingRedFlushCount_returnsCorrectCount() {
        when(invoiceMapper.selectCount(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(3L);
        long count = service.getPendingRedFlushCount();
        assertThat(count).isEqualTo(3L);
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
        invoice.setInvoiceType(InvoiceService.FIXED_INVOICE_TYPE);
        return invoice;
    }

    private BatchInvoiceItemRequest batchItem(int rowNumber, String companyName,
                                              String taxNumber, String amount) {
        BatchInvoiceItemRequest item = new BatchInvoiceItemRequest();
        item.setRowNumber(rowNumber);
        item.setCompanyName(companyName);
        item.setTaxNumber(taxNumber);
        item.setAmount(amount);
        item.setInvoiceType(InvoiceService.FIXED_INVOICE_TYPE);
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

    @Test
    void normalInvoiceDeducts1xQuota() {
        when(invoiceMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(501L);
            return 1;
        }).when(invoiceMapper).insert(any(Invoice.class));

        InvoiceResponse response = service.createInvoice(
                8L, "key-normal-1x", "测试公司", "91110000MA00000001",
                new BigDecimal("100.00"), "技术服务费", "NORMAL", "普票备注"
        );

        assertThat(response.invoiceCategory()).isEqualTo("NORMAL");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("100.00"), 501L);
    }

    @Test
    void specialVatInvoiceDeducts3xQuota() {
        when(invoiceMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(502L);
            return 1;
        }).when(invoiceMapper).insert(any(Invoice.class));

        InvoiceResponse response = service.createInvoice(
                8L, "key-special-3x", "测试公司", "91110000MA00000002",
                new BigDecimal("100.00"), "技术服务费", "VAT_SPECIAL", "专票备注"
        );

        assertThat(response.invoiceCategory()).isEqualTo("VAT_SPECIAL");
        // 专票扣除 3 倍额度：100.00 * 3 = 300.00
        verify(userQuotaService).deductQuota(8L, new BigDecimal("300.00"), 502L, "开票扣除 (专票3倍额度)");
    }

    @Test
    void cancellingSpecialVatInvoiceRefunds3xQuota() {
        Invoice invoice = invoice(503L, 8L, "PENDING");
        invoice.setAmount(new BigDecimal("100.00"));
        invoice.setInvoiceCategory("VAT_SPECIAL");
        when(invoiceMapper.selectById(503L)).thenReturn(invoice);
        when(invoiceMapper.update(any(), any())).thenReturn(1);

        service.cancelInvoice(503L, 8L);

        // 取消专票退还 3 倍额度：300.00
        verify(userQuotaService).refundQuota(8L, new BigDecimal("300.00"), 503L, "取消发票申请");
    }

    @Test
    void confirmingRedFlushOnSpecialVatInvoiceRefunds3xQuota() {
        Invoice invoice = invoice(504L, 8L, "COMPLETED");
        invoice.setAmount(new BigDecimal("100.00"));
        invoice.setInvoiceCategory("VAT_SPECIAL");
        invoice.setRedFlushStatus("PENDING");
        when(invoiceMapper.selectById(504L)).thenReturn(invoice);
        when(invoiceMapper.update(any(), any())).thenReturn(1);

        service.confirmRedFlush(504L, 1L, "冲红完成");

        // 红冲专票退还 3 倍额度：300.00
        verify(userQuotaService).refundQuota(eq(8L), eq(new BigDecimal("300.00")), eq(504L), any(), eq(1L), eq("ADMIN"));
    }

    @Test
    void adminUpdatingCategoryFromNormalToSpecialAdjustsQuotaDiff() {
        Invoice invoice = invoice(505L, 8L, "PENDING");
        invoice.setAmount(new BigDecimal("100.00"));
        invoice.setInvoiceCategory("NORMAL");
        when(invoiceMapper.selectById(505L)).thenReturn(invoice);
        when(invoiceMapper.update(any(), any())).thenReturn(1);

        service.adminUpdateInvoice(505L, "新公司", "91110000MA00000003",
                new BigDecimal("100.00"), "技术服务费", "VAT_SPECIAL", "改专票", 1L);

        // 额度从 100 变 300，差额为 +200.00
        verify(userQuotaService).adjustQuotaForInvoiceAmountChange(8L, new BigDecimal("200.00"), 505L, 1L);
    }

    @Test
    void acceptsChineseCategoryAliasesAndNormalizes() {
        when(invoiceMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(506L);
            return 1;
        }).when(invoiceMapper).insert(any(Invoice.class));

        InvoiceResponse response = service.createInvoice(
                8L, "key-chinese-alias", "测试公司", "91110000MA00000004",
                new BigDecimal("100.00"), "技术服务费", "专票", "别名测试"
        );

        assertThat(response.invoiceCategory()).isEqualTo("VAT_SPECIAL");
        verify(userQuotaService).deductQuota(8L, new BigDecimal("300.00"), 506L, "开票扣除 (专票3倍额度)");
    }

    @Test
    void rejectsRepeatedRequestWhenInvoiceCategoryDiffers() {
        Invoice existing = invoice(507L, 8L, "PENDING");
        existing.setCompanyName("测试公司");
        existing.setTaxNumber("91110000MA00000005");
        existing.setAmount(new BigDecimal("100.00"));
        existing.setInvoiceType("技术服务费");
        existing.setInvoiceCategory("NORMAL");
        existing.setIdempotencyKey("key-repeat-cat-mismatch");

        when(invoiceMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.createInvoice(
                8L, "key-repeat-cat-mismatch", "测试公司", "91110000MA00000005",
                new BigDecimal("100.00"), "技术服务费", "VAT_SPECIAL", null
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(40902);
            assertThat(ex.getMessage()).contains("Idempotency-Key 已用于其他发票申请");
        });
    }
}
