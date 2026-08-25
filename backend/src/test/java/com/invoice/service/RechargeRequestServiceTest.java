package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.invoice.dto.RechargeRequestDto;
import com.invoice.dto.RechargeReviewDto;
import com.invoice.entity.RechargeRequest;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.RechargeRequestMapper;
import com.invoice.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RechargeRequestServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), RechargeRequest.class);
    }

    @Mock
    private RechargeRequestMapper rechargeRequestMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserQuotaService userQuotaService;

    private RechargeRequestService service;

    @BeforeEach
    void setUp() {
        service = new RechargeRequestService(
                rechargeRequestMapper,
                userMapper,
                userQuotaService,
                "./target/test-uploads"
        );
    }

    @Test
    void createRequest_success() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setAmount(new BigDecimal("100.00"));
        dto.setScreenshotUrl("/recharge-requests/screenshot/abc.png");
        dto.setRemark("测试充值");

        RechargeRequest result = service.createRequest(userId, dto);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("PENDING", result.getStatus());
        verify(rechargeRequestMapper, times(1)).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_allows_multiple_pending_requests() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setAmount(new BigDecimal("50.00"));
        dto.setScreenshotUrl("/url.png");

        RechargeRequest result = service.createRequest(userId, dto);
        assertNotNull(result);
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        verify(rechargeRequestMapper, times(1)).insert(any(RechargeRequest.class));
    }

    @Test
    void reviewRequest_approved_success() {
        Long requestId = 10L;
        Long adminId = 99L;
        Long userId = 1L;

        RechargeRequest request = new RechargeRequest();
        request.setId(requestId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("200.00"));
        request.setStatus("PENDING");

        when(rechargeRequestMapper.selectById(requestId)).thenReturn(request);
        when(rechargeRequestMapper.update(isNull(), any())).thenReturn(1);

        RechargeReviewDto reviewDto = new RechargeReviewDto();
        reviewDto.setStatus("APPROVED");
        reviewDto.setAdminRemark("通过");

        service.reviewRequest(requestId, reviewDto, adminId);

        verify(userQuotaService, times(1)).rechargeQuota(
                eq(userId),
                eq(new BigDecimal("200.00")),
                eq(adminId),
                eq("充值申请审核通过"),
                eq("RECHARGE_REQUEST_10")
        );
    }

    @Test
    void reviewRequest_rejected_does_not_call_rechargeQuota() {
        Long requestId = 11L;
        Long adminId = 99L;
        Long userId = 1L;

        RechargeRequest request = new RechargeRequest();
        request.setId(requestId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("200.00"));
        request.setStatus("PENDING");

        when(rechargeRequestMapper.selectById(requestId)).thenReturn(request);
        when(rechargeRequestMapper.update(isNull(), any())).thenReturn(1);

        RechargeReviewDto reviewDto = new RechargeReviewDto();
        reviewDto.setStatus("REJECTED");
        reviewDto.setAdminRemark("凭证模糊");

        service.reviewRequest(requestId, reviewDto, adminId);

        verify(userQuotaService, never()).rechargeQuota(any(), any(), any(), any(), any());
    }
}
