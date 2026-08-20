package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户额度变更历史实体
 */
@Data
@TableName("user_quota_transaction")
public class UserQuotaTransaction {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易类型：RECHARGE-充值，DEDUCT-扣除，ADJUST-调整
     */
    private String transactionType;
    
    /**
     * 变更金额（正数表示增加，负数表示减少）
     */
    private BigDecimal amount;
    
    /**
     * 变更前余额
     */
    private BigDecimal balanceBefore;
    
    /**
     * 变更后余额
     */
    private BigDecimal balanceAfter;
    
    /**
     * 操作人ID（管理员操作时记录）
     */
    private Long operatorId;
    
    /**
     * 操作人类型：ADMIN-管理员，SYSTEM-系统
     */
    private String operatorType;
    
    /**
     * 关联的发票ID（扣除类型时记录）
     */
    private Long invoiceId;
    
    /**
     * 备注说明
     */
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}