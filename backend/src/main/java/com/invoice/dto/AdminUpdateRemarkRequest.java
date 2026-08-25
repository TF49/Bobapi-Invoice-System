package com.invoice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateRemarkRequest {

    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
