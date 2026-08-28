package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    
    private String password;
    
    /**
     * 角色：USER-普通用户，ADMIN-管理员，INVOICE_CLERK-开票员
     */
    private String role;

    /**
     * OpenAPI 调用密钥
     */
    private String apiKey;

    /**
     * API Key是否启用：1-启用，0-禁用
     */
    private Boolean apiKeyEnabled;

    /**
     * 管理员填写的用户备注
     */
    private String remark;

    private Boolean enabled;

    private Long authVersion;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
