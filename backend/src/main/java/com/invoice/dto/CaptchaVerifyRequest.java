package com.invoice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CaptchaVerifyRequest {

    @NotBlank(message = "验证码 ID 不能为空")
    @Size(max = 64, message = "验证码 ID 无效")
    private String captchaId;

    @NotNull(message = "滑块 X 坐标不能为空")
    @Min(value = 0, message = "滑块位置无效")
    @Max(value = 252, message = "滑块位置无效")
    private Integer x;

    @NotNull(message = "滑块 Y 坐标不能为空")
    @Min(value = 0, message = "滑块位置无效")
    @Max(value = 159, message = "滑块位置无效")
    private Integer y;
}
