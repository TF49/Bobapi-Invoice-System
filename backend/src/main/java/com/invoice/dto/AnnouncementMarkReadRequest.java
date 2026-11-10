package com.invoice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AnnouncementMarkReadRequest {

    @NotEmpty(message = "公告 ID 不能为空")
    @Size(max = 50, message = "一次最多标记 50 条公告")
    private List<@NotNull(message = "公告 ID 不能为空") @Positive(message = "公告 ID 必须大于 0") Long> ids;
}
