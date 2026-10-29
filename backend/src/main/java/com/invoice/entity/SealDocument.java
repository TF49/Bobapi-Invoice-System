package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盖章文件实体
 */
@Data
@TableName("seal_document")
public class SealDocument {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 申请用户ID
     */
    private Long userId;

    /**
     * OpenAPI 上传幂等键（网页端上传为空）
     */
    private String idempotencyKey;
    
    /**
     * 待盖章原始文件名
     */
    private String sourceFileName;
    
    /**
     * 待盖章原始文件存储物理路径
     */
    private String sourceFilePath;
    
    /**
     * 待盖章原始文件大小(字节)
     */
    private Long sourceFileSize;

    /**
     * 待盖章原始文件内容 SHA-256（小写十六进制，64 位）
     */
    @TableField("source_file_sha256")
    private String sourceFileSha256;
    
    /**
     * 待盖章原始文件扩展名
     */
    private String sourceFileExt;
    
    /**
     * 盖章交付文件名
     */
    private String sealedFileName;
    
    /**
     * 盖章交付文件存储物理路径
     */
    private String sealedFilePath;
    
    /**
     * 盖章交付文件大小(字节)
     */
    private Long sealedFileSize;
    
    /**
     * 服务费(固定 10.00 元)
     */
    private BigDecimal feeAmount;
    
    /**
     * 状态：PENDING-待盖章，COMPLETED-已完成，REJECTED-已驳回，CANCELLED-已取消
     */
    private String status;
    
    /**
     * 驳回原因
     */
    private String rejectReason;
    
    /**
     * 盖章交付操作人ID
     */
    private Long operatorId;
    
    /**
     * 用户端是否已处理/已下载标记：0-未处理，1-已处理
     */
    @TableField("is_processed")
    private Boolean isProcessed;
    
    /**
     * 交付完成时间
     */
    private LocalDateTime completedAt;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
