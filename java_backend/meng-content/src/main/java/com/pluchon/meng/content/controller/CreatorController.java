package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.CreatorOverviewVO;
import com.pluchon.meng.content.controller.vo.CreatorTopPostVO;
import com.pluchon.meng.content.controller.vo.CreatorTrendVO;
import com.pluchon.meng.content.service.CreatorService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 创作中心的数据接口，身份只取当前请求
@RestController
@RequestMapping("/content/creator")
public class CreatorController {

    // 作者统计查询
    @Autowired
    private CreatorService service;

    /** 返回自己现存帖子的累计计数。 */
    @GetMapping("/overview")
    public ApiResponse<CreatorOverviewVO> overview() {
        return ApiResponse.success(service.overview());
    }

    /** 返回按台北日期补齐的每日新增趋势。 */
    @GetMapping("/trend")
    public ApiResponse<List<CreatorTrendVO>> trend(
        @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.success(service.trend(days));
    }

    /** 返回自己的前五篇热门帖子。 */
    @GetMapping("/top-posts")
    public ApiResponse<List<CreatorTopPostVO>> topPosts() {
        return ApiResponse.success(service.topPosts());
    }
}
