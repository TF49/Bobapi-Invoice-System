package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票申请批次实体
 */
@Data
@TableName("invoice_batch")
public class InvoiceBatch {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 批次幂等键
     */
    private String idempotencyKey;
    
    /**
     * 请求内容SHA-256哈希
     */
    private String requestHash;
    
    /**
     * 批次总行数
     */
    private Integer totalCount;
    
    /**
     * 批次总金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 批次状态：COMPLETED-已完成
     */
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}