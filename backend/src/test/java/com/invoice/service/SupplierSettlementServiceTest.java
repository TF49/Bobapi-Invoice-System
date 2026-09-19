package com.invoice.service;

import com.invoice.dto.SupplierSettlementRequest;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.entity.SupplierSettlement;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.SupplierSettlementMapper;
import com.invoice.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierSettlementServiceTest {

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

        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                10L, 2L, 8L, new BigDecimal("10000.00"), new BigDecimal("1000.00")));
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(BigDecimal.ZERO);
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

        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                10L, 2L, 8L, new BigDecimal("10000.00"), new BigDecimal("1000.00")));
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(BigDecimal.ZERO);
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
    void testCreateSettlement_ExceedsUnsettledAmount() {
        BigDecimal amount = new BigDecimal("5000.00");
        Long operatorId = 1L;

        when(invoiceMapper.selectOverallStat()).thenReturn(new InvoiceMapper.OverallStat(
                5L, 1L, 4L, new BigDecimal("3000.00"), BigDecimal.ZERO));
        when(supplierSettlementMapper.selectTotalSettledAmount()).thenReturn(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supplierSettlementService.createSettlement(amount, "超额结算", operatorId)
        );
        assertEquals(40001, ex.getCode());
        verify(supplierSettlementMapper, never()).insert(any(SupplierSettlement.class));
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
}
