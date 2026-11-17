package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票流转事件（税号数据流时间线的数据来源）
 */
@Data
@TableName("invoice_event")
public class InvoiceEvent {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String QUOTA_DEDUCTED = "QUOTA_DEDUCTED";
    public static final String ADMIN_UPDATED = "ADMIN_UPDATED";
    public static final String ISSUED = "ISSUED";
    public static final String FILE_REPLACED = "FILE_REPLACED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RED_FLUSH_APPLIED = "RED_FLUSH_APPLIED";
    public static final String RED_FLUSH_CONFIRMED = "RED_FLUSH_CONFIRMED";
    public static final String RED_FLUSH_REJECTED = "RED_FLUSH_REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long invoiceId;

    /** 发票所属用户ID */
    private Long userId;

    private String eventType;

    /** 操作方：USER / ADMIN / API / SYSTEM */
    private String operatorType;

    private Long operatorId;

    /** 本事件对该发票额度的影响（负数扣除，正数退还） */
    private BigDecimal quotaDelta;

    /** 额度变动后用户余额（批量合并扣除时为空） */
    private BigDecimal balanceAfter;

    private Long quotaTransactionId;

    private String detail;

    private LocalDateTime createdAt;
}
