package com.invoice.dto;

import com.invoice.entity.UserQuota;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户额度响应
 */
@Data
public class UserQuotaResponse {
    
    private Long userId;
    private BigDecimal balance;
    private BigDecimal totalRecharged;
    private BigDecimal totalDeducted;
    
    public static UserQuotaResponse from(UserQuota quota) {
        UserQuotaResponse response = new UserQuotaResponse();
        response.setUserId(quota.getUserId());
        response.setBalance(quota.getBalance());
        response.setTotalRecharged(quota.getTotalRecharged());
        response.setTotalDeducted(quota.getTotalDeducted());
        return response;
    }
}