package com.invoice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员/开票员确认红冲标记请求
 */
@Data
public class RedFlushConfirmRequest {

    @Size(max = 500, message = "处理备注最多500个字符")
    private String remark;
}
