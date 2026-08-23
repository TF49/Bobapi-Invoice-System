package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUpdateRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(USER|INVOICE_CLERK|ADMIN)$", message = "角色只能是 USER、INVOICE_CLERK 或 ADMIN")
    private String role;
}
