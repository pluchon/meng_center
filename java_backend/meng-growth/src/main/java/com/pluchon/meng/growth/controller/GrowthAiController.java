package com.pluchon.meng.growth.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.growth.service.GrowthAssistantService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 基于本域统计的成长总结接口
@RestController
@RequestMapping("/growth")
public class GrowthAiController {

    // AI 辅助业务入口
    @Autowired
    private GrowthAssistantService assistant;

    public record Period(int days) {}

    /** 生成当前用户的成长小结。 */
    @PostMapping("/ai-summaries")
    public ApiResponse<Map<String, Object>> summarize(@RequestBody Period body) {
        return ApiResponse.success(assistant.summarize(body.days()));
    }
}
