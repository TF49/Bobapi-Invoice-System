package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户申请发票红冲请求
 */
@Data
public class RedFlushApplyRequest {

    @NotBlank(message = "红冲原因不能为空")
    @Size(max = 500, message = "红冲原因最多500个字符")
    private String reason;
}
