package com.invoice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户盖章独立额度响应
 */
@Data
public class UserSealQuotaResponse {
    
    private Long id;
    private Long userId;
    private String username;
    private BigDecimal balance;
    private BigDecimal totalRecharged;
    private BigDecimal totalDeducted;
    /**
     * 可用盖章次数（balance / 10）
     */
    private Integer availableCount;
    private LocalDateTime updatedAt;
}
