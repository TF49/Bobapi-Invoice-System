package com.invoice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.invoice.dto.TaxFlowResponse;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceEvent;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceEventMapper;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxFlowServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), Invoice.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), InvoiceEvent.class);
    }

    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceEventMapper invoiceEventMapper;
    @Mock private UserMapper userMapper;

    private TaxFlowService service;

    @BeforeEach
    void setUp() {
        service = new TaxFlowService(invoiceMapper, invoiceEventMapper, userMapper);
    }

    @Test
    void suggestIgnoresQueriesShorterThanFourCharacters() {
        assertThat(service.suggest(" 911 ", 8L)).isEmpty();
        assertThat(service.suggest(null, 8L)).isEmpty();
        verifyNoInteractions(invoiceMapper);
    }

    @Test
    void suggestNormalizesEscapesAndScopesToTheUser() {
        InvoiceMapper.TaxNumberSuggestionRow row = new InvoiceMapper.TaxNumberSuggestionRow();
        row.setTaxNumber("9111_AB%");
        row.setCompanyName("示例公司");
        row.setInvoiceCount(3L);
        when(invoiceMapper.selectTaxNumberSuggestions("9111\\_AB\\%", 8L, 10)).thenReturn(List.of(row));

        List<TaxFlowResponse.Suggestion> result = service.suggest(" 9111_ab% ", 8L);

        assertThat(result).containsExactly(new TaxFlowResponse.Suggestion("9111_AB%", "示例公司", 3L));
    }

    @Test
    void suggestPassesNullUserIdForAdminCrossUserSearch() {
        when(invoiceMapper.selectTaxNumberSuggestions(anyString(), eq(null), anyInt())).thenReturn(List.of());

        service.suggest("ABCD", null);

        verify(invoiceMapper).selectTaxNumberSuggestions("ABCD", null, 10);
    }

    @Test
    void flowRejectsBlankTaxNumber() {
        assertThatThrownBy(() -> service.getFlow("  ", 8L, 1, 20, false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40001);
    }

    @Test
    void flowReturnsEmptyResultWhenTheUserHasNoInvoiceForTheTaxNumber() {
        InvoiceMapper.TaxFlowSummaryRow summary = new InvoiceMapper.TaxFlowSummaryRow();
        summary.setInvoiceCount(0L);
        when(invoiceMapper.selectTaxFlowSummary("ABCDEFGH", 8L)).thenReturn(summary);

        TaxFlowResponse response = service.getFlow("abcdefgh", 8L, 1, 20, false, true);

        assertThat(response.total()).isZero();
        assertThat(response.invoices()).isEmpty();
        verify(invoiceMapper, never()).selectList(any());
    }

    @Test
    void flowGroupsEventsPerInvoiceAndHidesOwnerForUsers() {
        stubSummary("ABCDEFGH", 8L);
        Invoice first = invoice(1L, 8L);
        Invoice second = invoice(2L, 8L);
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of(first, second));
        when(invoiceMapper.selectObjs(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of("示例公司"));
        when(invoiceEventMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<InvoiceEvent>>any())).thenReturn(List.of(
                event(10L, 1L, InvoiceEvent.SUBMITTED, null, null),
                event(11L, 1L, InvoiceEvent.QUOTA_DEDUCTED, "-100.00", "900.00"),
                event(12L, 2L, InvoiceEvent.SUBMITTED, null, null)));

        TaxFlowResponse response = service.getFlow("ABCDEFGH", 8L, 1, 20, false, true);

        assertThat(response.companyNames()).containsExactly("示例公司");
        assertThat(response.summary().invoiceCount()).isEqualTo(2);
        assertThat(response.invoices()).hasSize(2);
        TaxFlowResponse.InvoiceFlow flow = response.invoices().get(0);
        assertThat(flow.userId()).isNull();
        assertThat(flow.username()).isNull();
        assertThat(flow.events()).extracting(TaxFlowResponse.Event::eventType)
                .containsExactly(InvoiceEvent.SUBMITTED, InvoiceEvent.QUOTA_DEDUCTED);
        assertThat(flow.events().get(1).balanceAfter()).isEqualByComparingTo("900.00");
        assertThat(response.invoices().get(1).events()).hasSize(1);
        verifyNoInteractions(userMapper);
    }

    @Test
    void adminFlowShowsOwnerAndClerkFlowHidesBalance() {
        stubSummary("ABCDEFGH", null);
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of(invoice(1L, 8L)));
        when(invoiceMapper.selectObjs(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of("示例公司"));
        when(invoiceEventMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<InvoiceEvent>>any())).thenReturn(List.of(
                event(11L, 1L, InvoiceEvent.QUOTA_DEDUCTED, "-100.00", "900.00")));
        User owner = new User();
        owner.setId(8L);
        owner.setUsername("alice");
        when(userMapper.selectBatchIds(List.of(8L))).thenReturn(List.of(owner));

        TaxFlowResponse response = service.getFlow("ABCDEFGH", null, 1, 20, true, false);

        TaxFlowResponse.InvoiceFlow flow = response.invoices().get(0);
        assertThat(flow.userId()).isEqualTo(8L);
        assertThat(flow.username()).isEqualTo("alice");
        assertThat(flow.events().get(0).quotaDelta()).isEqualByComparingTo("-100.00");
        assertThat(flow.events().get(0).balanceAfter()).isNull();
    }

    @Test
    void flowClampsPageSize() {
        stubSummary("ABCDEFGH", 8L);
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of());
        when(invoiceMapper.selectObjs(org.mockito.ArgumentMatchers.<Wrapper<Invoice>>any())).thenReturn(List.of());

        TaxFlowResponse response = service.getFlow("ABCDEFGH", 8L, 0, 500, false, true);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
    }

    private void stubSummary(String taxNumber, Long userId) {
        InvoiceMapper.TaxFlowSummaryRow summary = new InvoiceMapper.TaxFlowSummaryRow();
        summary.setInvoiceCount(2L);
        summary.setTotalAmount(new BigDecimal("300.00"));
        summary.setPendingCount(1L);
        summary.setCompletedCount(1L);
        summary.setCancelledCount(0L);
        summary.setRedFlushedCount(0L);
        summary.setActiveDeducted(new BigDecimal("300.00"));
        when(invoiceMapper.selectTaxFlowSummary(taxNumber, userId)).thenReturn(summary);
    }

    private static Invoice invoice(Long id, Long userId) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setUserId(userId);
        invoice.setTaxNumber("ABCDEFGH");
        invoice.setCompanyName("示例公司");
        invoice.setAmount(new BigDecimal("100.00"));
        invoice.setStatus("PENDING");
        invoice.setCreatedAt(LocalDateTime.now());
        return invoice;
    }

    private static InvoiceEvent event(Long id, Long invoiceId, String type, String delta, String balanceAfter) {
        InvoiceEvent event = new InvoiceEvent();
        event.setId(id);
        event.setInvoiceId(invoiceId);
        event.setUserId(8L);
        event.setEventType(type);
        event.setOperatorType("USER");
        event.setQuotaDelta(delta == null ? null : new BigDecimal(delta));
        event.setBalanceAfter(balanceAfter == null ? null : new BigDecimal(balanceAfter));
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
