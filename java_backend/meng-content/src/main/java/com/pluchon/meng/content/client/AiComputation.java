package com.pluchon.meng.content.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// AI 计算请求与结构化响应契约
public final class AiComputation {

    private AiComputation() {}

    public record EmbeddingsRequest(List<String> texts) {}

    public record EmbeddingsResponse(
        List<List<Float>> vectors,
        String modelVersion
    ) {}

    public record RetrievalRequest(
        String query,
        List<String> candidates,
        int limit,
        long userId
    ) {}

    public record RetrievalResponse(List<Integer> ranked, long tokens) {}

    public record DraftRequest(
        String category,
        String title,
        String content,
        List<String> candidates
    ) {}

    public record Tag(String name, boolean existing) {}

    public record TagsResponse(List<Tag> tags) {}

    public record Inspiration(
        String angle,
        String explanation,
        String opening
    ) {}

    public record InspirationsResponse(List<Inspiration> inspirations) {}

    public record CoverRequest(String title, String summary, String category) {}

    public record CreatorRequest(
        int days,
        List<Map<String, Object>> dailyMetrics,
        long postCount,
        List<Map<String, Object>> bestPosts,
        List<Map<String, Object>> categories,
        List<Map<String, Object>> postingHours
    ) {}

    public record SummaryResponse(String summary, List<String> suggestions) {}

    public record TrainingSample(
        List<Double> features,
        boolean clicked,
        OffsetDateTime occurredAt
    ) {}

    public record TrainingRequest(List<TrainingSample> samples) {}

    public record TrainingResponse(
        boolean eligible,
        List<Double> weights,
        Double intercept,
        Double auc,
        long sampleCount,
        long clickCount
    ) {}
}
