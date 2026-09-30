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
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, remark, operatorId);

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
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, null, operatorId);

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

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(amount, "提前预结", operatorId);
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

        when(supplierSettlementMapper.selectOne(any())).thenReturn(existing);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                new BigDecimal("1000.00"), "已存在结算", 1L, key);

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

        when(supplierSettlementMapper.insert(any(SupplierSettlement.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));
        when(supplierSettlementMapper.selectOne(any())).thenReturn(null, existing);

        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                new BigDecimal("2000.00"), "并发结算", 1L, key);

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
                        new BigDecimal("5000.00"), "不同备注", 1L, key));

        assertEquals(40902, exception.getCode());
        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
    }
}
