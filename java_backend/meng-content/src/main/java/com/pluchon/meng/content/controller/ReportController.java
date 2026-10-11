package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.service.ReportService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 举报接口
@RestController
@RequestMapping("/content/reports")
public class ReportController {

    // 对应接口的业务操作
    @Autowired
    private ReportService service;

    /** 提交帖子或评论举报。 */
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateReportDTO body) {
        service.create(body);
        return ApiResponse.success(null);
    }
}
