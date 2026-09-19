package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商结算记录实体
 */
@Data
@TableName("supplier_settlement")
public class SupplierSettlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 本次结算金额
     */
    private BigDecimal settlementAmount;

    /**
     * 结算备注
     */
    private String remark;

    /**
     * 操作人ID（管理员）
     */
    private Long operatorId;

    /**
     * 操作人用户名（冗余字段，保证用户删除后历史记录仍可查阅操作人信息）
     */
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
