package com.invoice.dto;

import com.invoice.entity.UserQuota;
import com.invoice.entity.UserSealQuota;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 用户额度响应（包含开票额度和盖章额度）
 */
@Data
public class UserQuotaResponse {
    
    private Long userId;
    
    // 开票额度
    private BigDecimal balance;
    private BigDecimal totalRecharged;
    private BigDecimal totalDeducted;
    
    // 盖章钱包额度
    private BigDecimal sealBalance;
    private BigDecimal sealTotalRecharged;
    private BigDecimal sealTotalDeducted;
    private Integer sealAvailableCount; // 可用盖章次数（sealBalance / 10）
    
    public static UserQuotaResponse from(UserQuota quota) {
        return from(quota, null);
    }

    public static UserQuotaResponse from(UserQuota quota, UserSealQuota sealQuota) {
        UserQuotaResponse response = new UserQuotaResponse();
        
        // 开票额度
        response.setUserId(quota.getUserId());
        response.setBalance(quota.getBalance());
        response.setTotalRecharged(quota.getTotalRecharged());
        response.setTotalDeducted(quota.getTotalDeducted());
        
        // 盖章钱包额度
        if (sealQuota != null) {
            response.setSealBalance(sealQuota.getBalance());
            response.setSealTotalRecharged(sealQuota.getTotalRecharged());
            response.setSealTotalDeducted(sealQuota.getTotalDeducted());
            
            // 计算可用盖章次数
            int count = sealQuota.getBalance() != null
                    ? sealQuota.getBalance().divide(new BigDecimal("10.00"), 0, RoundingMode.DOWN).intValue()
                    : 0;
            response.setSealAvailableCount(Math.max(0, count));
        } else {
            // 如果没有盖章额度记录，返回零值
            response.setSealBalance(BigDecimal.ZERO);
            response.setSealTotalRecharged(BigDecimal.ZERO);
            response.setSealTotalDeducted(BigDecimal.ZERO);
            response.setSealAvailableCount(0);
        }
        
        return response;
    }
}