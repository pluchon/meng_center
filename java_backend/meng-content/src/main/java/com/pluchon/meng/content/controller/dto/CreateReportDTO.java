package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.ReportReason;
import com.pluchon.meng.content.entity.enums.ReportTarget;
import com.pluchon.meng.content.validation.CodePointLength;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 举报提交参数
public record CreateReportDTO(
        // 举报目标类型
        @NotNull ReportTarget targetType,
        // 举报目标编号
        @Positive long targetId,
        // 举报原因
        @NotNull ReportReason reason,
        // 补充说明
        @CodePointLength(max = 200) String note) {}
