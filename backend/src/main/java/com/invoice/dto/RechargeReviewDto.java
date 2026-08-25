package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 充值申请审核请求
 */
@Data
public class RechargeReviewDto {
    
    /**
     * 审核结果：APPROVED-通过，REJECTED-拒绝
     */
    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "审核结果只能为 APPROVED 或 REJECTED")
    private String status;
    
    /**
     * 管理员审核备注
     */
    @Size(max = 500, message = "审核备注不能超过 500 个字符")
    private String adminRemark;
}