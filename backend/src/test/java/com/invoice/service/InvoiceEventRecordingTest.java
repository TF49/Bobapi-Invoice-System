package com.invoice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceBatch;
import com.invoice.entity.InvoiceEvent;
import com.invoice.entity.UserQuotaTransaction;
import com.invoice.mapper.InvoiceBatchMapper;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.RechargeRequestMapper;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserQuotaMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发票状态变更时写入流转事件（税号数据流）的测试
 */
@ExtendWith(MockitoExtension.class)
class InvoiceEventRecordingTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Invoice.class);
    }

    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceBatchMapper invoiceBatchMapper;
    @Mock private UserQuotaService userQuotaService;
    @Mock private UserMapper userMapper;
    @Mock private UserQuotaMapper userQuotaMapper;
    @Mock private RechargeRequestMapper rechargeRequestMapper;
    @Mock private SupplierSettlementService supplierSettlementService;
    @Mock private InvoiceEventService invoiceEventService;

    @TempDir
    Path uploadDirectory;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceMapper, invoiceBatchMapper, userQuotaService, userMapper,
                userQuotaMapper, rechargeRequestMapper, supplierSettlementService, uploadDirectory.toString());
        service.initializeUploadDirectory();
        service.setInvoiceEventService(invoiceEventService);
    }

    @Test
    void createInvoiceRecordsSubmissionAndDeductionLinkedToTheQuotaTransaction() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(99L);
            return 1;
        });
        UserQuotaTransaction tx = transaction(3095L, "900.00", "开票扣除");
        when(userQuotaService.deductQuota(8L, new BigDecimal("100.00"), 99L)).thenReturn(tx);

        service.createInvoice(8L, "12345678-1234-1234-1234-123456789099",
                "示例公司", "ABCDEFGHIJKLMNO", new BigDecimal("100.00"), InvoiceService.FIXED_INVOICE_TYPE, null);

        var order = inOrder(invoiceEventService);
        order.verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.SUBMITTED), eq("USER"), eq(8L),
                isNull(), isNull(), org.mockito.ArgumentMatchers.contains("网页提交"));
        order.verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.QUOTA_DEDUCTED), eq("USER"), eq(8L),
                eq(new BigDecimal("-100.00")), eq(tx), eq("开票扣除"));
    }

    @Test
    void openApiInvoiceRecordsApiAsOperator() {
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(100L);
            return 1;
        });

        service.createOpenInvoice(8L, "ORDER-1", null, "示例公司", "ABCDEFGHIJKLMNO",
                new BigDecimal("50.00"), InvoiceService.FIXED_INVOICE_TYPE, null);

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.SUBMITTED), eq("API"), eq(8L),
                isNull(), isNull(), org.mockito.ArgumentMatchers.contains("ORDER-1"));
        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.QUOTA_DEDUCTED), eq("API"), eq(8L),
                eq(new BigDecimal("-50.00")), isNull(), anyString());
    }

    @Test
    void batchCreationRecordsTwoEventsPerInvoiceWithItsOwnQuotaShare() {
        doAnswer(invocation -> {
            InvoiceBatch batch = invocation.getArgument(0);
            batch.setId(50L);
            return 1;
        }).when(invoiceBatchMapper).insert(any(InvoiceBatch.class));
        when(invoiceMapper.insertBatch(anyList())).thenReturn(2);
        Invoice first = batchInvoice(101L, 2, "100.00");
        Invoice second = batchInvoice(102L, 4, "20.50");
        when(invoiceMapper.selectByBatchId(50L)).thenReturn(List.of(first, second));
        when(userQuotaService.deductBatchQuota(8L, new BigDecimal("120.50"), 50L))
                .thenReturn(transaction(3093L, "879.50", "批量开票扣除(批次#50)"));

        service.createInvoicesBatch(8L, "batch-1234567890123456", List.of(
                batchItem(2, "示例公司", "ABCDE12345678901", "100"),
                batchItem(4, "示例公司", "ABCDE12345678901", "20.5")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InvoiceEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceEventService).recordAll(captor.capture());
        List<InvoiceEvent> events = captor.getValue();
        assertThat(events).extracting(InvoiceEvent::getInvoiceId, InvoiceEvent::getEventType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, InvoiceEvent.SUBMITTED),
                        org.assertj.core.groups.Tuple.tuple(101L, InvoiceEvent.QUOTA_DEDUCTED),
                        org.assertj.core.groups.Tuple.tuple(102L, InvoiceEvent.SUBMITTED),
                        org.assertj.core.groups.Tuple.tuple(102L, InvoiceEvent.QUOTA_DEDUCTED));
        InvoiceEvent secondDeduct = events.get(3);
        assertThat(secondDeduct.getQuotaDelta()).isEqualByComparingTo("-20.50");
        assertThat(secondDeduct.getQuotaTransactionId()).isEqualTo(3093L);
        assertThat(secondDeduct.getBalanceAfter()).isNull();
        assertThat(secondDeduct.getDetail()).contains("批次 #50").contains("本票占 ¥20.50");
        assertThat(events.get(2).getDetail()).contains("第 4 行");
    }

    @Test
    void cancelRecordsRefundEvent() {
        Invoice pending = invoice(100L, 8L, "PENDING");
        pending.setDeductedQuota(new BigDecimal("420.00"));
        when(invoiceMapper.selectById(100L)).thenReturn(pending);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);
        UserQuotaTransaction tx = transaction(3100L, "1420.00", "取消发票申请");
        when(userQuotaService.refundQuota(8L, new BigDecimal("420.00"), 100L, "取消发票申请")).thenReturn(tx);

        service.cancelInvoice(100L, 8L, false);

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.CANCELLED), eq("USER"), eq(8L),
                eq(new BigDecimal("420.00")), eq(tx), anyString());
    }

    @Test
    void openApiCancelRecordsApiAsOperator() {
        Invoice pending = invoice(100L, 8L, "PENDING");
        pending.setDeductedQuota(new BigDecimal("100.00"));
        when(invoiceMapper.selectById(100L)).thenReturn(pending);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        service.cancelOpenInvoice(8L, 100L);

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.CANCELLED), eq("API"), eq(8L),
                eq(new BigDecimal("100.00")), isNull(), anyString());
    }

    @Test
    void reapplyingAfterRejectionIsRecordedAsANewRound() {
        Invoice completed = invoice(201L, 8L, "COMPLETED");
        completed.setRedFlushStatus("REJECTED");
        when(invoiceMapper.selectById(201L)).thenReturn(completed);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        service.applyRedFlush(201L, 8L, "抬头写错");

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.RED_FLUSH_APPLIED), eq("USER"), eq(8L),
                isNull(), isNull(), eq("再次申请红冲，原因：抬头写错"));
    }

    @Test
    void confirmingRedFlushRecordsRefund() {
        Invoice completed = invoice(205L, 8L, "COMPLETED");
        completed.setRedFlushStatus("PENDING");
        completed.setDeductedQuota(new BigDecimal("150.00"));
        when(invoiceMapper.selectById(205L)).thenReturn(completed);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        service.confirmRedFlush(205L, 1L, "已作废");

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.RED_FLUSH_CONFIRMED), eq("ADMIN"), eq(1L),
                eq(new BigDecimal("150.00")), isNull(), eq("确认红冲申请，退还额度，备注：已作废"));
    }

    @Test
    void rejectingRedFlushRecordsReason() {
        Invoice completed = invoice(206L, 8L, "COMPLETED");
        completed.setRedFlushStatus("PENDING");
        when(invoiceMapper.selectById(206L)).thenReturn(completed);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);

        service.rejectRedFlush(206L, 1L, "已入账");

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.RED_FLUSH_REJECTED), eq("ADMIN"), eq(1L),
                isNull(), isNull(), eq("驳回红冲，原因：已入账"));
    }

    @Test
    void adminUpdateRecordsChangedFieldsAndQuotaDifference() {
        Invoice existing = invoice(14L, 8L, "PENDING");
        existing.setDeductedQuota(new BigDecimal("100.00"));
        when(invoiceMapper.selectById(14L)).thenReturn(existing);
        UserQuotaTransaction tx = transaction(3200L, "500.00", "管理员调整发票补扣额度");
        when(userQuotaService.adjustQuotaForInvoiceAmountChange(8L, new BigDecimal("100.00"), 14L, 1L)).thenReturn(tx);

        service.adminUpdateInvoice(14L, "示例公司", "ABCDEFGHIJKLMNO",
                new BigDecimal("200.00"), InvoiceService.FIXED_INVOICE_TYPE, null, 1L);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.ADMIN_UPDATED), eq("ADMIN"), eq(1L),
                eq(new BigDecimal("-100.00")), eq(tx), detail.capture());
        assertThat(detail.getValue()).isEqualTo("金额 100.00 → 200.00；补扣额度 ¥100.00");
    }

    @Test
    void uploadingTheFirstFileRecordsIssued() throws Exception {
        Invoice pending = invoice(2L, 8L, "PENDING");
        when(invoiceMapper.selectById(2L)).thenReturn(pending);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "发票.png", "image/png", pngBytes());

        service.uploadInvoiceFile(2L, file, 1L);

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.ISSUED), eq("ADMIN"), eq(1L),
                isNull(), isNull(), eq("上传发票文件，开票完成：发票.png"));
    }

    @Test
    void replacingTheFileOfACompletedInvoiceRecordsFileReplaced() throws Exception {
        Invoice completed = invoice(3L, 8L, "COMPLETED");
        when(invoiceMapper.selectById(3L)).thenReturn(completed);
        when(invoiceMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "新发票.png", "image/png", pngBytes());

        service.uploadInvoiceFile(3L, file, 1L);

        verify(invoiceEventService).record(any(Invoice.class), eq(InvoiceEvent.FILE_REPLACED), eq("ADMIN"), eq(1L),
                isNull(), isNull(), eq("替换发票文件：新发票.png"));
    }

    private static UserQuotaTransaction transaction(Long id, String balanceAfter, String remark) {
        UserQuotaTransaction tx = new UserQuotaTransaction();
        tx.setId(id);
        tx.setBalanceAfter(new BigDecimal(balanceAfter));
        tx.setRemark(remark);
        return tx;
    }

    private static Invoice invoice(Long id, Long userId, String status) {
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

    private static Invoice batchInvoice(Long id, int rowNumber, String amount) {
        Invoice invoice = invoice(id, 8L, "PENDING");
        invoice.setBatchId(50L);
        invoice.setBatchRowNumber(rowNumber);
        invoice.setAmount(new BigDecimal(amount));
        invoice.setDeductedQuota(new BigDecimal(amount));
        return invoice;
    }

    private static BatchInvoiceItemRequest batchItem(int rowNumber, String companyName, String taxNumber, String amount) {
        BatchInvoiceItemRequest item = new BatchInvoiceItemRequest();
        item.setRowNumber(rowNumber);
        item.setCompanyName(companyName);
        item.setTaxNumber(taxNumber);
        item.setAmount(amount);
        item.setInvoiceType(InvoiceService.FIXED_INVOICE_TYPE);
        return item;
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
