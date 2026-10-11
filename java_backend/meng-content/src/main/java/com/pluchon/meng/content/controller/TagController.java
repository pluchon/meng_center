package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.TagService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 标签匹配接口
@RestController
@RequestMapping("/content/tags")
public class TagController {

    // 对应接口的业务操作
    @Autowired
    private TagService service;

    /** 匹配系统已有标签。 */
    @GetMapping
    public ApiResponse<List<String>> suggest(
            @RequestParam(defaultValue = "") @Size(max = 48) String keyword,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return ApiResponse.success(service.suggest(keyword, size));
    }
}
