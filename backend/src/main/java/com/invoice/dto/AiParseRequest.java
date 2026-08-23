package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 智能识别发票信息请求 DTO
 */
@Data
public class AiParseRequest {

    /**
     * 用户粘贴的原始文本，AI 将从中提取发票信息
     */
    @NotBlank(message = "识别文本不能为空")
    @Size(max = 2000, message = "识别文本不能超过 2000 个字符")
    private String text;
}
