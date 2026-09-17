package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票实体
 */
@Data
@TableName("invoice")
public class Invoice {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 公司名称
     */
    private String companyName;
    
    /**
     * 税号
     */
    private String taxNumber;
    
    /**
     * 开票金额
     */
    private BigDecimal amount;

    /**
     * 开票类型（如：技术服务费）
     */
    private String invoiceType;

    /**
     * 发票票种：NORMAL-普通发票（普票），VAT_SPECIAL-增值税专用发票（专票）
     */
    private String invoiceCategory;

    /**
     * 备注
     */
    private String remark;

    /**
     * 外部商户订单号（用于OpenAPI对接）
     */
    private String outTradeNo;
    
    /**
     * 状态：PENDING-待开票，COMPLETED-已开票，CANCELLED-已取消
     */
    private String status;

    /**
     * 用户已处理标记：0-未处理，1-已处理
     */
    @TableField("is_processed")
    private Boolean isProcessed;

    /**
     * 红冲状态：NONE-未申请，PENDING-待红冲，COMPLETED-已红冲，REJECTED-已驳回
     */
    private String redFlushStatus;

    /**
     * 用户申请红冲原因
     */
    private String redFlushReason;

    /**
     * 开票员/管理员处理备注
     */
    private String redFlushRemark;

    /**
     * 红冲申请时间
     */
    private LocalDateTime redFlushApplyTime;

    /**
     * 红冲完成/处理时间
     */
    private LocalDateTime redFlushCompleteTime;

    /**
     * 红冲处理操作人ID
     */
    private Long redFlushOperatorId;
    
    /**
     * 发票文件路径
     */
    private String filePath;

    /**
     * 用户上传时的原始文件名
     */
    private String fileName;

    /**
     * 用户维度的创建请求幂等键
     */
    private String idempotencyKey;

    /**
     * 批次ID（批量申请时关联）
     */
    private Long batchId;

    /**
     * 批次内原始行号（用于审计）
     */
    private Integer batchRowNumber;

    /**
     * 提交方式：API-API提交，MANUAL-手动提交
     */
    private String submissionType;

    /**
     * 用户ID
     */
    private Long userId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 实际完成开票的时间；待开票时为空
     */
    private LocalDateTime completedAt;
    
    @TableLogic
    private Integer deleted;
}
