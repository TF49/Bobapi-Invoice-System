package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户盖章额度变更流水实体
 */
@Data
@TableName("user_seal_transaction")
public class UserSealTransaction {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易类型：RECHARGE-充值，DEDUCT-扣除，REFUND-退还，ADJUST-调整
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
     * 关联盖章文件ID
     */
    private Long documentId;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人类型：ADMIN-管理员，SYSTEM-系统，USER-用户
     */
    private String operatorType;
    
    /**
     * 备注说明
     */
    private String remark;
    
    /**
     * 幂等键
     */
    private String idempotencyKey;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
