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
     * 结算通道-出票主体：DEFAULT-普通公司，TECH-科技公司
     */
    private String sellerCompany;

    /**
     * 结算通道-票种：NORMAL-普通发票，VAT_SPECIAL-增值税专用发票
     */
    private String invoiceCategory;

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

    /**
     * 结算请求幂等键
     */
    private String idempotencyKey;

    /**
     * 作废时间，NULL 表示有效。作废记录不计入任何已结算汇总
     */
    private LocalDateTime voidedAt;

    /**
     * 作废操作人ID
     */
    private Long voidedBy;

    /**
     * 作废操作人用户名（冗余存储）
     */
    private String voidedByName;

    /**
     * 作废原因
     */
    private String voidReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
