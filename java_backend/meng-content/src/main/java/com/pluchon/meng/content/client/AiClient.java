package com.pluchon.meng.content.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "meng-ai", contextId = "contentAi", url = "${meng.content.ai.url:}")
// AI 计算接口与调用超时约束
public interface AiClient {
    tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
        .findAndAddModules()
        .build();

    private static Map<String, Object> output(Object value) {
        return JSON.convertValue(
            value,
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
        );
    }

    private static feign.Request.Options deadline(int milliseconds) {
        return new feign.Request.Options(
            500,
            java.util.concurrent.TimeUnit.MILLISECONDS,
            milliseconds,
            java.util.concurrent.TimeUnit.MILLISECONDS,
            false
        );
    }

    @PostMapping("/ai/internal/embeddings")
    AiComputation.EmbeddingsResponse computeEmbeddings(
        @RequestBody AiComputation.EmbeddingsRequest body,
        feign.Request.Options options
    );

    @PostMapping("/ai/internal/tag-suggestions")
    AiComputation.TagsResponse computeTags(
        @RequestBody AiComputation.DraftRequest body,
        feign.Request.Options options
    );

    @PostMapping("/ai/internal/inspirations")
    AiComputation.InspirationsResponse computeInspirations(
        @RequestBody AiComputation.DraftRequest body,
        feign.Request.Options options
    );

    @PostMapping("/ai/internal/covers")
    byte[] computeCovers(
        @RequestBody AiComputation.CoverRequest body,
        feign.Request.Options options
    );

    @PostMapping("/ai/internal/creator-reviews")
    AiComputation.SummaryResponse computeReview(
        @RequestBody AiComputation.CreatorRequest body,
        feign.Request.Options options
    );

    @PostMapping("/ai/internal/ranker-trainings")
    AiComputation.TrainingResponse computeTraining(
        @RequestBody AiComputation.TrainingRequest body,
        feign.Request.Options options
    );

    default Map<String, Object> embeddings(Map<String, Object> body) {
        return output(
            computeEmbeddings(
                JSON.convertValue(body, AiComputation.EmbeddingsRequest.class),
                deadline(2000)
            )
        );
    }

    default Map<String, Object> tags(Map<String, Object> body) {
        return output(
            computeTags(JSON.convertValue(body, AiComputation.DraftRequest.class), deadline(8000))
        );
    }

    default Map<String, Object> inspirations(Map<String, Object> body) {
        return output(
            computeInspirations(
                JSON.convertValue(body, AiComputation.DraftRequest.class),
                deadline(15000)
            )
        );
    }

    default byte[] covers(Map<String, Object> body) {
        return computeCovers(
            JSON.convertValue(body, AiComputation.CoverRequest.class),
            deadline(60000)
        );
    }

    default Map<String, Object> review(Map<String, Object> body) {
        return output(
            computeReview(
                JSON.convertValue(body, AiComputation.CreatorRequest.class),
                deadline(15000)
            )
        );
    }

    default Map<String, Object> train(Map<String, Object> body) {
        return output(
            computeTraining(
                JSON.convertValue(body, AiComputation.TrainingRequest.class),
                deadline(120000)
            )
        );
    }
}
