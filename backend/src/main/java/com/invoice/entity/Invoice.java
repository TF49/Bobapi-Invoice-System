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
     * 状态：PENDING-待开票，COMPLETED-已开票
     */
    private String status;
    
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
     * 用户ID
     */
    private Long userId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
