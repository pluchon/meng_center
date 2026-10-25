package com.pluchon.meng.content.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// AI 计算请求与结构化响应契约
public final class AiComputation {

    private AiComputation() {}

    public record EmbeddingsRequest(List<String> texts, String textType) {}

    public record EmbeddingsResponse(
        List<List<Float>> vectors,
        String modelVersion
    ) {}

    public record RetrievalRequest(
        String query,
        List<String> candidates,
        int limit,
        long userId,
        String scene
    ) {}

    public record RetrievalResponse(List<Integer> ranked, List<Double> scores, boolean reranked) {}

    public static List<Integer> ordered(RetrievalResponse result,int candidates) {
        if(result==null || result.ranked()==null || new java.util.HashSet<>(result.ranked()).size()!=result.ranked().size() ||
            result.ranked().stream().anyMatch(i -> i==null || i<0 || i>=candidates))
            throw new IllegalArgumentException("重排下标不合法");
        if(!result.reranked()) return result.ranked();
        if(result.scores()==null || result.scores().size()!=result.ranked().size() ||
            result.scores().stream().anyMatch(s -> s==null || !Double.isFinite(s) || s<0 || s>1))
            throw new IllegalArgumentException("重排分数不合法");
        return java.util.stream.IntStream.range(0,result.ranked().size()).boxed()
            .sorted(java.util.Comparator.comparingDouble((Integer i) -> result.scores().get(i)).reversed())
            .map(i -> result.ranked().get(i)).toList();
    }

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
