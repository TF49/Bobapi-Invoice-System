package com.invoice.dto;

import com.invoice.entity.UserQuotaTransaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户额度变更历史响应
 */
@Data
public class UserQuotaTransactionResponse {
    
    private Long id;
    private Long userId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long operatorId;
    private String operatorType;
    private Long invoiceId;
    private String remark;
    private LocalDateTime createdAt;
    
    public static UserQuotaTransactionResponse from(UserQuotaTransaction transaction) {
        UserQuotaTransactionResponse response = new UserQuotaTransactionResponse();
        response.setId(transaction.getId());
        response.setUserId(transaction.getUserId());
        response.setTransactionType(transaction.getTransactionType());
        response.setAmount(transaction.getAmount());
        response.setBalanceBefore(transaction.getBalanceBefore());
        response.setBalanceAfter(transaction.getBalanceAfter());
        response.setOperatorId(transaction.getOperatorId());
        response.setOperatorType(transaction.getOperatorType());
        response.setInvoiceId(transaction.getInvoiceId());
        response.setRemark(transaction.getRemark());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}