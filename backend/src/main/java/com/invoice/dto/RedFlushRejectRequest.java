package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员/开票员驳回红冲申请请求
 */
@Data
public class RedFlushRejectRequest {

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因最多500个字符")
    private String reason;
}
