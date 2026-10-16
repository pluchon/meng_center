package com.pluchon.meng.growth.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// AI 计算接口与调用超时约束
@FeignClient(name = "meng-ai", contextId = "growthAi", url = "${meng.growth.ai.url:}")
public interface AiClient {
    record SummaryRequest(
        int days,
        java.util.List<Map<String, Object>> dailyExperience,
        java.util.List<Map<String, Object>> sources,
        int checkInDays,
        int longestStreak,
        int level,
        long nextLevelExperience
    ) {}

    record SummaryResponse(String summary, java.util.List<String> suggestions) {}

    @PostMapping("/ai/internal/growth-summaries")
    SummaryResponse compute(@RequestBody SummaryRequest stats);

    default Map<String, Object> summarize(Map<String, Object> stats) {
        var json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        return json.convertValue(
            compute(json.convertValue(stats, SummaryRequest.class)),
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
        );
    }
}
