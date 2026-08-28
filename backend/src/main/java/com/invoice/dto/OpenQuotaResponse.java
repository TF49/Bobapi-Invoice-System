package com.invoice.dto;

import com.invoice.entity.UserQuota;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开放接口额度查询响应
 */
@Data
public class OpenQuotaResponse {

    private BigDecimal balance;
    private BigDecimal totalRecharged;
    private BigDecimal totalDeducted;

    public static OpenQuotaResponse from(UserQuota quota) {
        OpenQuotaResponse response = new OpenQuotaResponse();
        response.setBalance(quota != null && quota.getBalance() != null ? quota.getBalance() : BigDecimal.ZERO);
        response.setTotalRecharged(quota != null && quota.getTotalRecharged() != null ? quota.getTotalRecharged() : BigDecimal.ZERO);
        response.setTotalDeducted(quota != null && quota.getTotalDeducted() != null ? quota.getTotalDeducted() : BigDecimal.ZERO);
        return response;
    }
}