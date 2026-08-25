package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值申请实体
 */
@Data
@TableName("recharge_request")
public class RechargeRequest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 申请充值金额
     */
    private BigDecimal amount;
    
    /**
     * 充值截图URL
     */
    private String screenshotUrl;
    
    /**
     * 申请状态：PENDING-待审核，APPROVED-已通过，REJECTED-已拒绝
     */
    private String status;
    
    /**
     * 用户备注
     */
    private String remark;
    
    /**
     * 管理员审核备注
     */
    private String adminRemark;
    
    /**
     * 审核管理员ID
     */
    private Long reviewedBy;
    
    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}