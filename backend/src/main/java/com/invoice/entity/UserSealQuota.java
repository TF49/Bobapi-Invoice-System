package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户盖章独立额度实体（10元/次）
 */
@Data
@TableName("user_seal_quota")
public class UserSealQuota {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 当前剩余盖章额度(元)
     */
    private BigDecimal balance;
    
    /**
     * 累计充值金额(元)
     */
    private BigDecimal totalRecharged;
    
    /**
     * 累计扣除金额(元)
     */
    private BigDecimal totalDeducted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
