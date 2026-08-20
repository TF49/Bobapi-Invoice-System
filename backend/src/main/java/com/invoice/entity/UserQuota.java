package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户额度实体
 */
@Data
@TableName("user_quota")
public class UserQuota {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 当前剩余额度
     */
    private BigDecimal balance;
    
    /**
     * 总充值金额
     */
    private BigDecimal totalRecharged;
    
    /**
     * 总扣除金额
     */
    private BigDecimal totalDeducted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}