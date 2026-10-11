package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.*;
import com.pluchon.meng.content.validation.CodePointLength;

import jakarta.validation.constraints.*;

public record CreateReportDTO(
        @NotNull ReportTarget targetType,
        @Positive long targetId,
        @NotNull ReportReason reason,
        @CodePointLength(max = 200) String note) {}
