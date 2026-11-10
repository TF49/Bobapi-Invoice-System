package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementSaveRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 20000, message = "公告内容不能超过 20000 个字符")
    private String content;

    @NotNull(message = "公告分类不能为空")
    @Pattern(regexp = "^(FEATURE|MAINTENANCE|IMPORTANT|GENERAL)$",
            message = "公告分类只能是 FEATURE、MAINTENANCE、IMPORTANT 或 GENERAL")
    private String category;
}
