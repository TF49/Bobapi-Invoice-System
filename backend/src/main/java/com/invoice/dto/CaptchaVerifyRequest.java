package com.invoice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    /**
     * 拖动轨迹，每个点为 [x, y, t]（x：拼图块偏移，y：纵向偏移，t：相对按下时刻的毫秒数）。
     * 详细规则由服务端校验，此处只限制体积。
     */
    @NotNull(message = "请拖动滑块完成验证")
    @Size(min = 1, max = 400, message = "滑块轨迹无效")
    private List<@NotNull(message = "滑块轨迹无效") @Size(min = 3, max = 3, message = "滑块轨迹无效") List<Integer>> track;
}
