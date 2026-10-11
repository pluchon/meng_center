package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.SearchPostVO;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.service.PostSearchService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 帖子搜索接口
@RestController
@RequestMapping("/content/search")
@Validated
public class SearchController {

    // 对应接口的业务操作
    @Autowired
    private PostSearchService service;

    /** 按关键词、分类和排序分页搜索帖子。 */
    @GetMapping("/posts")
    public ApiResponse<PageResult<SearchPostVO>> posts(
            @RequestParam String q,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(defaultValue = "HOT") PostSort sort,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) long size) {
        return ApiResponse.success(service.search(q, category, sort, page, size));
    }
}
