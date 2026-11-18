package com.invoice.service;

import com.invoice.dto.SupplierSettlementRequest;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.entity.SupplierSettlement;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.SupplierSettlementMapper;
import com.invoice.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierSettlementServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SupplierSettlement.class);
    }

    @Mock
    private SupplierSettlementMapper supplierSettlementMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private InvoiceMapper invoiceMapper;

    @InjectMocks
    private SupplierSettlementService supplierSettlementService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setRole("ADMIN");
    }

    @Test
    void testCreateSettlement_Success() {
        // Arrange
        BigDecimal amount = new BigDecimal("1000.00");
        String remark = "测试结算";
        Long operatorId = 1L;

        SupplierSettlement mockSettlement = new SupplierSettlement();
        mockSettlement.setId(1L);
        mockSettlement.setSettlementAmount(amount);
        mockSettlement.setRemark(remark);
        mockSettlement.setOperatorId(operatorId);
        mockSettlement.setOperatorName("admin");
        mockSettlement.setCreatedAt(LocalDateTime.now());

        when(supplierSettlementMapper.insert(any(SupplierSettlement.class))).thenReturn(1);
        when(userMapper.selectById(operatorId)).thenReturn(testUser);

        // Act
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, "DEFAULT", "NORMAL", remark, operatorId, null);

        // Assert
        assertNotNull(response);
        assertEquals(amount, response.settlementAmount());
        assertEquals(remark, response.remark());
        assertEquals("admin", response.operatorName());
        assertNotNull(response.createdAt());

        verify(supplierSettlementMapper, times(1)).insert(any(SupplierSettlement.class));
        verify(userMapper, times(1)).selectById(operatorId);
    }

    @Test
    void testCreateSettlement_WithoutRemark() {
        // Arrange
        BigDecimal amount = new BigDecimal("500.00");
        Long operatorId = 1L;

        when(supplierSettlementMapper.insert(any(SupplierSettlement.class))).thenReturn(1);
        when(userMapper.selectById(operatorId)).thenReturn(testUser);

        // Act
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, "DEFAULT", "NORMAL", null, operatorId, null);

        // Assert
        assertNotNull(response);
        assertEquals(amount, response.settlementAmount());
        assertNull(response.remark());
        assertEquals("admin", response.operatorName());

        verify(supplierSettlementMapper, times(1)).insert(any(SupplierSettlement.class));
    }

    @Test
    void testCreateSettlement_ExceedsUnsettledAmount_Allowed() {
        BigDecimal amount = new BigDecimal("5000.00");
        Long operatorId = 1L;

        when(supplierSettlementMapper.insert(any(SupplierSettlement.class))).thenReturn(1);
        when(userMapper.selectById(operatorId)).thenReturn(testUser);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, "TECH", "VAT_SPECIAL", "提前预结", operatorId, null);
        assertNotNull(response);
        assertEquals(amount, response.settlementAmount());
        assertEquals("提前预结", response.remark());
        assertEquals("admin", response.operatorName());
        verify(supplierSettlementMapper, times(1)).insert(any(SupplierSettlement.class));
    }

    @Test
    void testGetSettlementHistory_Empty() {
        // Arrange
        when(supplierSettlementMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Act
        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();

        // Assert
        assertNotNull(history);
        assertTrue(history.isEmpty());

        verify(supplierSettlementMapper, times(1)).selectList(any());
    }

    @Test
    void testGetSettlementHistory_WithRedundantOperatorName() {
        // Arrange
        SupplierSettlement settlement1 = new SupplierSettlement();
        settlement1.setId(1L);
        settlement1.setSettlementAmount(new BigDecimal("1000.00"));
        settlement1.setRemark("第一次结算");
        settlement1.setOperatorId(1L);
        settlement1.setOperatorName("admin");
        settlement1.setCreatedAt(LocalDateTime.now());

        List<SupplierSettlement> mockSettlements = List.of(settlement1);

        when(supplierSettlementMapper.selectList(any())).thenReturn(mockSettlements);

        // Act
        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("1000.00"), history.get(0).settlementAmount());
        assertEquals("第一次结算", history.get(0).remark());
        assertEquals("admin", history.get(0).operatorName());

        verify(supplierSettlementMapper, times(1)).selectList(any());
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    void testGetSettlementHistory_LegacyWithoutOperatorName() {
        // Arrange
        SupplierSettlement settlement1 = new SupplierSettlement();
        settlement1.setId(1L);
        settlement1.setSettlementAmount(new BigDecimal("1000.00"));
        settlement1.setRemark("第一次结算");
        settlement1.setOperatorId(1L);
        settlement1.setOperatorName(null);
        settlement1.setCreatedAt(LocalDateTime.now());

        List<SupplierSettlement> mockSettlements = List.of(settlement1);

        when(supplierSettlementMapper.selectList(any())).thenReturn(mockSettlements);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(testUser));

        // Act
        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("admin", history.get(0).operatorName());

        verify(supplierSettlementMapper, times(1)).selectList(any());
        verify(userMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void testGetTotalSettledAmount_Zero() {
        // Arrange
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(null);

        // Act
        BigDecimal total = supplierSettlementService.getTotalSettledAmount();

        // Assert
        assertNotNull(total);
        assertEquals(BigDecimal.ZERO, total);

        verify(supplierSettlementMapper, times(1)).selectTotalSettledAmount();
    }

    @Test
    void testGetTotalSettledAmount_WithValue() {
        // Arrange
        BigDecimal expectedTotal = new BigDecimal("5000.00");
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(expectedTotal);

        // Act
        BigDecimal total = supplierSettlementService.getTotalSettledAmount();

        // Assert
        assertNotNull(total);
        assertEquals(expectedTotal, total);

        verify(supplierSettlementMapper, times(1)).selectTotalSettledAmount();
    }

    @Test
    void testGetTotalUnsettledAmount_Positive() {
        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                10L, 2L, 8L, new BigDecimal("10000.00"), new BigDecimal("1000.00")));
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(new BigDecimal("3000.00"));

        BigDecimal unsettled = supplierSettlementService.getTotalUnsettledAmount();
        assertEquals(new BigDecimal("7000.00"), unsettled);
    }

    @Test
    void testGetTotalUnsettledAmount_NegativeWhenPreSettled() {
        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                10L, 2L, 8L, new BigDecimal("10000.00"), new BigDecimal("1000.00")));
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(new BigDecimal("15000.00"));

        BigDecimal unsettled = supplierSettlementService.getTotalUnsettledAmount();
        assertEquals(new BigDecimal("-5000.00"), unsettled);
    }

    @Test
    void testCreateSettlement_WithIdempotencyKey_ReturnsExisting() {
        String key = "settlement-unique-123";
        SupplierSettlement existing = new SupplierSettlement();
        existing.setId(99L);
        existing.setSettlementAmount(new BigDecimal("1000.00"));
        existing.setRemark("已存在结算");
        existing.setOperatorId(1L);
        existing.setOperatorName("admin");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setIdempotencyKey(key);
        existing.setSellerCompany("DEFAULT");
        existing.setInvoiceCategory("NORMAL");

        when(supplierSettlementMapper.selectOne(any())).thenReturn(existing);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                new BigDecimal("1000.00"), "DEFAULT", "NORMAL", "已存在结算", 1L, key);

        assertNotNull(response);
        assertEquals(99L, response.id());
        assertEquals(new BigDecimal("1000.00"), response.settlementAmount());
        assertEquals("admin", response.operatorName());
        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
    }

    @Test
    void testCreateSettlement_WithIdempotencyKey_ConcurrentDuplicateRecovers() {
        String key = "settlement-race-456";
        when(userMapper.selectById(1L)).thenReturn(testUser);

        SupplierSettlement existing = new SupplierSettlement();
        existing.setId(100L);
        existing.setSettlementAmount(new BigDecimal("2000.00"));
        existing.setRemark("并发结算");
        existing.setOperatorId(1L);
        existing.setOperatorName("admin");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setIdempotencyKey(key);
        existing.setSellerCompany("DEFAULT");
        existing.setInvoiceCategory("NORMAL");

        when(supplierSettlementMapper.insert(any(SupplierSettlement.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));
        when(supplierSettlementMapper.selectOne(any())).thenReturn(null, existing);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                new BigDecimal("2000.00"), "DEFAULT", "NORMAL", "并发结算", 1L, key);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(new BigDecimal("2000.00"), response.settlementAmount());
    }

    @Test
    void testCreateSettlement_WithIdempotencyKey_RejectsDifferentPayload() {
        String key = "settlement-payload-123";
        SupplierSettlement existing = new SupplierSettlement();
        existing.setId(101L);
        existing.setSettlementAmount(new BigDecimal("1000.00"));
        existing.setRemark("原始备注");
        existing.setOperatorId(1L);
        existing.setCreatedAt(LocalDateTime.now());
        when(supplierSettlementMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                supplierSettlementService.createSettlement(
                        new BigDecimal("5000.00"), "DEFAULT", "NORMAL", "不同备注", 1L, key));

        assertEquals(40902, exception.getCode());
        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
    }

    @Test
    void testCreateSettlement_PersistsNormalizedChannel() {
        when(supplierSettlementMapper.insert(any(SupplierSettlement.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(testUser);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                new BigDecimal("300.00"), " tech ", "vat_special", "专票结算", 1L, null);

        org.mockito.ArgumentCaptor<SupplierSettlement> captor = org.mockito.ArgumentCaptor.forClass(SupplierSettlement.class);
        verify(supplierSettlementMapper).insert(captor.capture());
        assertEquals("TECH", captor.getValue().getSellerCompany());
        assertEquals("VAT_SPECIAL", captor.getValue().getInvoiceCategory());
        assertEquals("TECH", response.sellerCompany());
        assertEquals("VAT_SPECIAL", response.invoiceCategory());
    }

    @Test
    void testCreateSettlement_RejectsMissingOrInvalidChannel() {
        BusinessException missing = assertThrows(BusinessException.class, () ->
                supplierSettlementService.createSettlement(new BigDecimal("100.00"), null, "NORMAL", null, 1L, null));
        assertEquals(40001, missing.getCode());

        BusinessException invalid = assertThrows(BusinessException.class, () ->
                supplierSettlementService.createSettlement(new BigDecimal("100.00"), "DEFAULT", "ELECTRONIC", null, 1L, null));
        assertEquals(40001, invalid.getCode());

        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
    }

    @Test
    void testCreateSettlement_WithIdempotencyKey_RejectsDifferentChannel() {
        String key = "settlement-channel-789";
        SupplierSettlement existing = new SupplierSettlement();
        existing.setId(102L);
        existing.setSettlementAmount(new BigDecimal("1000.00"));
        existing.setSellerCompany("DEFAULT");
        existing.setInvoiceCategory("NORMAL");
        existing.setRemark("同一备注");
        existing.setOperatorId(1L);
        existing.setCreatedAt(LocalDateTime.now());
        existing.setIdempotencyKey(key);
        when(supplierSettlementMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                supplierSettlementService.createSettlement(
                        new BigDecimal("1000.00"), "TECH", "NORMAL", "同一备注", 1L, key));

        assertEquals(40902, exception.getCode());
        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
    }

    @Test
    void testGetSettledAmountByChannel_GroupsByChannelKey() {
        when(supplierSettlementMapper.selectSettledAmountByChannel()).thenReturn(List.of(
                new SupplierSettlementMapper.ChannelSettledStat("DEFAULT", "NORMAL", new BigDecimal("800000.00")),
                new SupplierSettlementMapper.ChannelSettledStat("TECH", "VAT_SPECIAL", new BigDecimal("2000.00"))
        ));

        java.util.Map<String, BigDecimal> result = supplierSettlementService.getSettledAmountByChannel();

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("800000.00"), result.get(SupplierSettlementService.channelKey("DEFAULT", "NORMAL")));
        assertEquals(new BigDecimal("2000.00"), result.get(SupplierSettlementService.channelKey("TECH", "VAT_SPECIAL")));
        assertNull(result.get(SupplierSettlementService.channelKey("DEFAULT", "VAT_SPECIAL")));
    }

    @Test
    void testGetHistory_ReturnsChannel() {
        SupplierSettlement s = new SupplierSettlement();
        s.setId(1L);
        s.setSettlementAmount(new BigDecimal("100.00"));
        s.setSellerCompany("TECH");
        s.setInvoiceCategory("NORMAL");
        s.setOperatorId(1L);
        s.setOperatorName("admin");
        s.setCreatedAt(LocalDateTime.now());
        when(supplierSettlementMapper.selectList(any())).thenReturn(List.of(s));

        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();

        assertEquals("TECH", history.get(0).sellerCompany());
        assertEquals("NORMAL", history.get(0).invoiceCategory());
    }

    private SupplierSettlement activeSettlement(Long id) {
        SupplierSettlement s = new SupplierSettlement();
        s.setId(id);
        s.setSettlementAmount(new BigDecimal("100.00"));
        s.setSellerCompany("DEFAULT");
        s.setInvoiceCategory("NORMAL");
        s.setOperatorId(1L);
        s.setOperatorName("creator");
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    @Test
    void testVoidSettlement_MarksVoidedWithOperatorAndReason() {
        when(supplierSettlementMapper.selectById(7L)).thenReturn(activeSettlement(7L));
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(supplierSettlementMapper.markVoided(eq(7L), any(LocalDateTime.class), eq(1L), eq("admin"), eq("选错通道"))).thenReturn(1);

        SupplierSettlementResponse response = supplierSettlementService.voidSettlement(7L, "  选错通道 ", 1L);

        assertTrue(response.voided());
        assertNotNull(response.voidedAt());
        assertEquals("admin", response.voidedByName());
        assertEquals("选错通道", response.voidReason());
        assertEquals("creator", response.operatorName());
    }

    @Test
    void testVoidSettlement_RejectsBlankReason() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                supplierSettlementService.voidSettlement(7L, "   ", 1L));
        assertEquals(40001, ex.getCode());
        verify(supplierSettlementMapper, never()).markVoided(any(), any(), any(), any(), any());
    }

    @Test
    void testVoidSettlement_NotFound() {
        when(supplierSettlementMapper.selectById(8L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                supplierSettlementService.voidSettlement(8L, "原因", 1L));
        assertEquals(40401, ex.getCode());
    }

    @Test
    void testVoidSettlement_AlreadyVoided() {
        SupplierSettlement s = activeSettlement(9L);
        s.setVoidedAt(LocalDateTime.now());
        when(supplierSettlementMapper.selectById(9L)).thenReturn(s);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                supplierSettlementService.voidSettlement(9L, "原因", 1L));
        assertEquals(40903, ex.getCode());
        verify(supplierSettlementMapper, never()).markVoided(any(), any(), any(), any(), any());
    }

    @Test
    void testVoidSettlement_ConcurrentVoidLosesRace() {
        when(supplierSettlementMapper.selectById(10L)).thenReturn(activeSettlement(10L));
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(supplierSettlementMapper.markVoided(eq(10L), any(), any(), any(), any())).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                supplierSettlementService.voidSettlement(10L, "原因", 1L));
        assertEquals(40903, ex.getCode());
    }

    @Test
    void testGetHistory_ExposesVoidInfo() {
        SupplierSettlement s = activeSettlement(11L);
        s.setVoidedAt(LocalDateTime.now());
        s.setVoidedByName("admin");
        s.setVoidReason("填错金额");
        when(supplierSettlementMapper.selectList(any())).thenReturn(List.of(s, activeSettlement(12L)));

        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();

        assertTrue(history.get(0).voided());
        assertEquals("填错金额", history.get(0).voidReason());
        assertFalse(history.get(1).voided());
        assertNull(history.get(1).voidedAt());
    }
}
