package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.invoice.dto.RechargeRequestDto;
import com.invoice.dto.RechargeReviewDto;
import com.invoice.entity.RechargeRequest;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.RechargeRequestMapper;
import com.invoice.mapper.UserMapper;
import com.invoice.security.RateLimitService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
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

    @Mock
    private RateLimitService rateLimitService;

    private RechargeRequestService service;

    @BeforeEach
    void setUp() {
        service = new RechargeRequestService(
                rechargeRequestMapper,
                userMapper,
                userQuotaService,
                rateLimitService,
                "./target/test-uploads"
        );
        lenient().when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));
    }

    @Test
    void createRequest_success() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setFeeAmount(new BigDecimal("300.00"));
        dto.setScreenshotUrl("/recharge-requests/screenshot/abc.png");
        dto.setRemark("测试充值");

        RechargeRequest result = service.createRequest(userId, dto, "recharge-1234567890123456");

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(new BigDecimal("300.00"), result.getFeeAmount());
        assertEquals(new BigDecimal("10000.00"), result.getAmount());
        assertEquals("recharge-1234567890123456", result.getIdempotencyKey());
        assertEquals("PENDING", result.getStatus());
        verify(rechargeRequestMapper, times(1)).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_roundsQuotaDownToTwoDecimals() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setFeeAmount(new BigDecimal("100.00"));
        dto.setScreenshotUrl("/url.png");

        RechargeRequest result = service.createRequest(userId, dto, "recharge-1234567890123457");

        assertEquals(new BigDecimal("100.00"), result.getFeeAmount());
        assertEquals(new BigDecimal("3333.33"), result.getAmount());
    }

    @Test
    void createRequest_rejectsInvalidFeeAmounts() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        for (BigDecimal invalidFee : new BigDecimal[]{
                BigDecimal.ZERO,
                new BigDecimal("-1.00"),
                new BigDecimal("100.001"),
                new BigDecimal("30000.00")
        }) {
            RechargeRequestDto dto = new RechargeRequestDto();
            dto.setFeeAmount(invalidFee);
            dto.setScreenshotUrl("/url.png");
            assertThrows(BusinessException.class,
                    () -> service.createRequest(userId, dto, "recharge-invalid-123456"));
        }

        RechargeRequestDto nullFeeDto = new RechargeRequestDto();
        nullFeeDto.setScreenshotUrl("/url.png");
        assertThrows(BusinessException.class,
                () -> service.createRequest(userId, nullFeeDto, "recharge-invalid-123456"));
        verify(rechargeRequestMapper, never()).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_allows_multiple_pending_requests() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setFeeAmount(new BigDecimal("50.00"));
        dto.setScreenshotUrl("/url.png");

        RechargeRequest first = service.createRequest(userId, dto, "recharge-1234567890123458");
        RechargeRequest second = service.createRequest(userId, dto, "recharge-1234567890123459");

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(new BigDecimal("50.00"), first.getFeeAmount());
        assertEquals(new BigDecimal("1666.66"), first.getAmount());
        verify(rechargeRequestMapper, times(2)).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_returnsExistingRequestForSameIdempotencyKeyAndPayload() {
        Long userId = 1L;
        User user = user(userId);
        when(userMapper.selectById(userId)).thenReturn(user);

        RechargeRequest existing = new RechargeRequest();
        existing.setId(12L);
        existing.setUserId(userId);
        existing.setFeeAmount(new BigDecimal("300.00"));
        existing.setAmount(new BigDecimal("10000.00"));
        existing.setScreenshotUrl("/proof.png");
        existing.setRemark("same request");
        when(rechargeRequestMapper.selectOne(any())).thenReturn(existing);

        RechargeRequestDto dto = requestDto("300.00", "/proof.png", " same request ");
        RechargeRequest result = service.createRequest(userId, dto, "recharge-duplicate-123456");

        assertSame(existing, result);
        verify(rechargeRequestMapper, never()).insert(any(RechargeRequest.class));
        verify(rateLimitService, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    void createRequest_rateLimitsOnlyNewRequests() {
        Long userId = 1L;
        when(userMapper.selectById(userId)).thenReturn(user(userId));
        when(rateLimitService.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitService.RateLimitResult(false, 23));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createRequest(
                        userId,
                        requestDto("300.00", "/proof.png", null),
                        "recharge-rate-limit-123456"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals(42908, exception.getCode());
        assertEquals(23L, exception.getRetryAfterSeconds());
        verify(rechargeRequestMapper, never()).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_rejectsDifferentPayloadForSameIdempotencyKey() {
        Long userId = 1L;
        when(userMapper.selectById(userId)).thenReturn(user(userId));

        RechargeRequest existing = new RechargeRequest();
        existing.setFeeAmount(new BigDecimal("300.00"));
        existing.setScreenshotUrl("/proof.png");
        when(rechargeRequestMapper.selectOne(any())).thenReturn(existing);

        RechargeRequestDto dto = requestDto("301.00", "/proof.png", null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createRequest(userId, dto, "recharge-conflict-1234567"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(40903, exception.getCode());
        verify(rechargeRequestMapper, never()).insert(any(RechargeRequest.class));
    }

    @Test
    void createRequest_recoversFromConcurrentDuplicateInsert() {
        Long userId = 1L;
        when(userMapper.selectById(userId)).thenReturn(user(userId));

        RechargeRequest existing = new RechargeRequest();
        existing.setId(13L);
        existing.setUserId(userId);
        existing.setFeeAmount(new BigDecimal("300.00"));
        existing.setAmount(new BigDecimal("10000.00"));
        existing.setScreenshotUrl("/proof.png");
        when(rechargeRequestMapper.selectOne(any())).thenReturn(null, existing);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(rechargeRequestMapper).insert(any(RechargeRequest.class));

        RechargeRequest result = service.createRequest(
                userId, requestDto("300.00", "/proof.png", null), "recharge-race-12345678901");

        assertSame(existing, result);
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

    private User user(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setRole("USER");
        return user;
    }

    private RechargeRequestDto requestDto(String feeAmount, String screenshotUrl, String remark) {
        RechargeRequestDto dto = new RechargeRequestDto();
        dto.setFeeAmount(new BigDecimal(feeAmount));
        dto.setScreenshotUrl(screenshotUrl);
        dto.setRemark(remark);
        return dto;
    }
}
