package com.invoice.service;

import com.invoice.dto.UserSealQuotaResponse;
import com.invoice.entity.User;
import com.invoice.entity.UserSealQuota;
import com.invoice.entity.UserSealTransaction;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserSealQuotaMapper;
import com.invoice.mapper.UserSealTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSealQuotaServiceTest {

    @Mock
    private UserSealQuotaMapper userSealQuotaMapper;

    @Mock
    private UserSealTransactionMapper userSealTransactionMapper;

    @Mock
    private UserMapper userMapper;

    private UserSealQuotaService service;

    @BeforeEach
    void setUp() {
        service = new UserSealQuotaService(userSealQuotaMapper, userSealTransactionMapper, userMapper);
    }

    @Test
    void calculatesAvailableCountProperlyInResponse() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("25.00"));
        quota.setTotalRecharged(new BigDecimal("50.00"));
        quota.setTotalDeducted(new BigDecimal("25.00"));

        User user = new User();
        user.setId(2L);
        user.setUsername("testuser");

        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserSealQuotaResponse response = service.getUserQuotaResponse(2L);
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals(new BigDecimal("25.00"), response.getBalance());
        assertEquals(2, response.getAvailableCount()); // 25 / 10 = 2 次
    }

    @Test
    void deductsQuotaSuccessfullyWhenBalanceIsSufficient() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("20.00"));
        quota.setTotalDeducted(BigDecimal.ZERO);

        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);

        service.deductQuota(2L, 100L, new BigDecimal("10.00"), "盖章扣费");

        assertEquals(new BigDecimal("10.00"), quota.getBalance());
        assertEquals(new BigDecimal("10.00"), quota.getTotalDeducted());
        verify(userSealQuotaMapper).updateById(quota);

        ArgumentCaptor<UserSealTransaction> captor = ArgumentCaptor.forClass(UserSealTransaction.class);
        verify(userSealTransactionMapper).insert(captor.capture());
        assertEquals("DEDUCT", captor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-10.00"), captor.getValue().getAmount());
        assertEquals(100L, captor.getValue().getDocumentId());
    }

    @Test
    void rejectsDeductWhenBalanceIsInsufficient() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("5.00"));

        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deductQuota(2L, 100L, new BigDecimal("10.00"), "盖章扣费"));

        assertEquals(40002, ex.getCode());
        verify(userSealQuotaMapper, never()).updateById(any(UserSealQuota.class));
        verify(userSealTransactionMapper, never()).insert(any(UserSealTransaction.class));
    }

    @Test
    void refundsQuotaSuccessfully() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("10.00"));
        quota.setTotalDeducted(new BigDecimal("20.00"));

        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);

        service.refundQuota(2L, 100L, new BigDecimal("10.00"), "撤销退费");

        assertEquals(new BigDecimal("20.00"), quota.getBalance());
        assertEquals(new BigDecimal("10.00"), quota.getTotalDeducted());
        verify(userSealQuotaMapper).updateById(quota);

        ArgumentCaptor<UserSealTransaction> captor = ArgumentCaptor.forClass(UserSealTransaction.class);
        verify(userSealTransactionMapper).insert(captor.capture());
        assertEquals("REFUND", captor.getValue().getTransactionType());
        assertEquals(new BigDecimal("10.00"), captor.getValue().getAmount());
    }

    @Test
    void rechargesQuotaWithIdempotencyProtection() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("10.00"));
        quota.setTotalRecharged(new BigDecimal("10.00"));

        // 第一次：没有相同 idempotencyKey
        when(userSealTransactionMapper.selectOne(any())).thenReturn(null);
        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);

        service.rechargeQuota(2L, new BigDecimal("50.00"), 1L, "充值", "RECHARGE_KEY_1");

        assertEquals(new BigDecimal("60.00"), quota.getBalance());
        assertEquals(new BigDecimal("60.00"), quota.getTotalRecharged());
        verify(userSealQuotaMapper).updateById(quota);

        // 第二次：相同 idempotencyKey，直接返回不重复充值
        UserSealTransaction existing = new UserSealTransaction();
        existing.setIdempotencyKey("RECHARGE_KEY_1");
        when(userSealTransactionMapper.selectOne(any())).thenReturn(existing);

        UserSealQuota result2 = service.rechargeQuota(2L, new BigDecimal("50.00"), 1L, "重复充值", "RECHARGE_KEY_1");
        assertEquals(new BigDecimal("60.00"), result2.getBalance());
    }

    @Test
    void adjustsQuotaAndPreventsNegativeBalance() {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(2L);
        quota.setBalance(new BigDecimal("10.00"));
        quota.setTotalDeducted(BigDecimal.ZERO);

        when(userSealQuotaMapper.selectOne(any())).thenReturn(quota);

        // 扣除超出余额应该拦截
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adjustQuota(2L, new BigDecimal("-20.00"), 1L, "扣费过多"));

        assertEquals(40005, ex.getCode());
        verify(userSealQuotaMapper, never()).updateById(any(UserSealQuota.class));

        // 正常增加额度
        service.adjustQuota(2L, new BigDecimal("30.00"), 1L, "管理员手动增加");
        assertEquals(new BigDecimal("40.00"), quota.getBalance());
        verify(userSealQuotaMapper).updateById(quota);
    }
}
