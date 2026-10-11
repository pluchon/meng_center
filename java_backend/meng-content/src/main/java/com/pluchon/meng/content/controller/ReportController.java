package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.service.ReportService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/content/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateReportDTO body) {
        service.create(body);
        return ApiResponse.success(null);
    }
}
