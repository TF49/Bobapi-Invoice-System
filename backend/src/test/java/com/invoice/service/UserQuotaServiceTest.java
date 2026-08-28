package com.invoice.service;

import com.invoice.entity.User;
import com.invoice.entity.UserQuota;
import com.invoice.entity.UserQuotaTransaction;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserQuotaMapper;
import com.invoice.mapper.UserQuotaTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQuotaServiceTest {

    @Mock
    private UserQuotaMapper userQuotaMapper;

    @Mock
    private UserQuotaTransactionMapper transactionMapper;

    @Mock
    private UserMapper userMapper;

    private UserQuotaService service;

    @BeforeEach
    void setUp() {
        service = new UserQuotaService(userQuotaMapper, transactionMapper, userMapper);
    }

    @Test
    void serializesRechargeAndStoresItsIdempotencyKey() {
        UserQuota quota = quota("100.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);
        when(transactionMapper.selectOne(any())).thenReturn(null);

        UserQuota result = service.rechargeQuota(2L, new BigDecimal("25.00"), 1L,
                "  充值  ", "quota-recharge-123456");

        assertEquals(new BigDecimal("125.00"), result.getBalance());
        verify(userQuotaMapper).updateById(quota);
        ArgumentCaptor<UserQuotaTransaction> captor = ArgumentCaptor.forClass(UserQuotaTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals("RECHARGE", captor.getValue().getTransactionType());
        assertEquals("quota-recharge-123456", captor.getValue().getIdempotencyKey());
        assertEquals("  充值  ", captor.getValue().getRemark());
    }

    @Test
    void reusesSameRechargeWithoutUpdatingQuotaAgain() {
        UserQuota quota = quota("100.00");
        UserQuotaTransaction existing = transaction("RECHARGE", "25.00", "充值", "quota-recharge-123456");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);
        when(transactionMapper.selectOne(any())).thenReturn(existing);

        UserQuota result = service.rechargeQuota(2L, new BigDecimal("25.00"), 1L,
                "  充值  ", "quota-recharge-123456");

        assertEquals(new BigDecimal("100.00"), result.getBalance());
        verify(userQuotaMapper, never()).updateById(any(UserQuota.class));
        verify(transactionMapper, never()).insert(any(UserQuotaTransaction.class));
    }

    @Test
    void rejectsReusingKeyForDifferentQuotaOperation() {
        UserQuotaTransaction existing = transaction("RECHARGE", "25.00", "充值", "quota-recharge-123456");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota("100.00"));
        when(transactionMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjustQuota(2L, new BigDecimal("25.00"), 1L,
                        "充值", "quota-recharge-123456"));

        assertEquals(40902, exception.getCode());
    }

    @Test
    void rejectsQuotaOperationForNonUserRole() {
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(user(1L, "ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rechargeQuota(1L, new BigDecimal("25.00"), 9L,
                        null, "quota-recharge-123456"));

        assertEquals(40904, exception.getCode());
        verify(userQuotaMapper, never()).selectOne(any());
    }

    @Test
    void locksAndChecksUserBeforeDeductingQuota() {
        UserQuota quota = quota("100.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        service.deductQuota(2L, new BigDecimal("40.00"), 10L);

        assertEquals(new BigDecimal("60.00"), quota.getBalance());
        verify(userMapper).selectByIdForUpdate(2L);
        verify(userQuotaMapper).updateById(quota);
    }

    @Test
    void deductsBatchQuotaSuccessfully() {
        UserQuota quota = quota("500.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        service.deductBatchQuota(2L, new BigDecimal("200.00"), 88L);

        assertEquals(new BigDecimal("300.00"), quota.getBalance());
        assertEquals(new BigDecimal("200.00"), quota.getTotalDeducted());
        verify(userMapper).selectByIdForUpdate(2L);
        verify(userQuotaMapper).updateById(quota);

        ArgumentCaptor<UserQuotaTransaction> captor = ArgumentCaptor.forClass(UserQuotaTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals("DEDUCT", captor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-200.00"), captor.getValue().getAmount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getRemark().contains("88"));
    }

    @Test
    void rejectsBatchQuotaDeductionWithInsufficientBalance() {
        UserQuota quota = quota("50.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deductBatchQuota(2L, new BigDecimal("200.00"), 88L));

        assertEquals(40002, exception.getCode());
        verify(userQuotaMapper, never()).updateById(any(UserQuota.class));
        verify(transactionMapper, never()).insert(any(UserQuotaTransaction.class));
    }

    @Test
    void autoCreatesMissingQuotaRecordsAndSyncsFromTransactionsInBatchQuery() {
        UserQuotaTransaction tx = transaction("RECHARGE", "10000.00", "充值", "key-1");
        tx.setBalanceBefore(BigDecimal.ZERO);
        tx.setBalanceAfter(new BigDecimal("10000.00"));

        when(userQuotaMapper.selectList(any())).thenReturn(java.util.List.of());
        when(transactionMapper.selectList(any())).thenReturn(java.util.List.of(tx));

        java.util.List<UserQuota> quotas = service.getQuotasByUserIds(java.util.List.of(2L));

        assertEquals(1, quotas.size());
        assertEquals(2L, quotas.get(0).getUserId());
        assertEquals(new BigDecimal("10000.00"), quotas.get(0).getBalance());
        assertEquals(new BigDecimal("10000.00"), quotas.get(0).getTotalRecharged());
        verify(userQuotaMapper).insert(any(UserQuota.class));
    }

    @Test
    void adjustsQuotaForInvoiceAmountIncreaseWithSufficientQuota() {
        UserQuota quota = quota("100.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        service.adjustQuotaForInvoiceAmountChange(2L, new BigDecimal("50.00"), 14L, 1L);

        assertEquals(new BigDecimal("50.00"), quota.getBalance());
        assertEquals(new BigDecimal("50.00"), quota.getTotalDeducted());
        verify(userQuotaMapper).updateById(quota);

        ArgumentCaptor<UserQuotaTransaction> captor = ArgumentCaptor.forClass(UserQuotaTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals("DEDUCT", captor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-50.00"), captor.getValue().getAmount());
        assertEquals(1L, captor.getValue().getOperatorId());
        assertEquals(14L, captor.getValue().getInvoiceId());
    }

    @Test
    void rejectsInvoiceAmountIncreaseWithInsufficientQuota() {
        UserQuota quota = quota("30.00");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjustQuotaForInvoiceAmountChange(2L, new BigDecimal("50.00"), 14L, 1L));

        assertEquals(40002, exception.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("用户剩余额度不足"));
        verify(userQuotaMapper, never()).updateById(any(UserQuota.class));
        verify(transactionMapper, never()).insert(any(UserQuotaTransaction.class));
    }

    @Test
    void adjustsQuotaForInvoiceAmountDecreaseAndRefunds() {
        UserQuota quota = quota("30.00");
        quota.setTotalDeducted(new BigDecimal("100.00"));
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, "USER"));
        when(userQuotaMapper.selectOne(any())).thenReturn(quota);

        service.adjustQuotaForInvoiceAmountChange(2L, new BigDecimal("-20.00"), 14L, 1L);

        assertEquals(new BigDecimal("50.00"), quota.getBalance());
        assertEquals(new BigDecimal("80.00"), quota.getTotalDeducted());
        verify(userQuotaMapper).updateById(quota);

        ArgumentCaptor<UserQuotaTransaction> captor = ArgumentCaptor.forClass(UserQuotaTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals("ADJUST", captor.getValue().getTransactionType());
        assertEquals(new BigDecimal("20.00"), captor.getValue().getAmount());
    }

    private UserQuota quota(String balance) {
        UserQuota quota = new UserQuota();
        quota.setId(7L);
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal(balance));
        quota.setTotalRecharged(BigDecimal.ZERO);
        quota.setTotalDeducted(BigDecimal.ZERO);
        return quota;
    }

    private UserQuotaTransaction transaction(String type, String amount, String remark, String key) {
        UserQuotaTransaction transaction = new UserQuotaTransaction();
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setRemark(remark);
        transaction.setIdempotencyKey(key);
        return transaction;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
