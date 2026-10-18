package com.pluchon.meng.companion.client;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.companion.service.CompanionError;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 只调用注册的 AI 服务，不接受用户传入目标地址
@Component
public class AiClient {

    @Autowired
    private HttpClient http;

    @Autowired
    private DiscoveryClient discovery;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    private final java.util.concurrent.atomic.AtomicInteger nextInstance =
        new java.util.concurrent.atomic.AtomicInteger();

    private URI endpoint(String path) {
        var instances = discovery.getInstances("meng-ai");
        if (instances.isEmpty()) throw new BusinessException(
            CompanionError.AI_UNAVAILABLE
        );
        return instances
            .get(
                Math.floorMod(nextInstance.getAndIncrement(), instances.size())
            )
            .getUri()
            .resolve("/ai/internal/companion/" + path);
    }

    public JsonNode reply(
        Map<String, Object> body,
        BiConsumer<String, JsonNode> progress
    ) {
        var request = HttpRequest.newBuilder(endpoint("replies"))
            .timeout(Duration.ofSeconds(105))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(body)
                )
            )
            .build();
        try {
            var subscriber = new Events(progress);
            var future = http.sendAsync(
                request,
                HttpResponse.BodyHandlers.fromLineSubscriber(subscriber)
            );
            try {
                var response = future.get(105, TimeUnit.SECONDS);
                if (response.statusCode() != 200 || subscriber.result == null) {
                    if (subscriber.failure != null) throw subscriber.failure;
                    throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                }
                if (subscriber.failure != null) throw subscriber.failure;
                return subscriber.result;
            } finally {
                // Future 的截止时间覆盖响应体，避免仅限制响应头而无限等待流。
                future.cancel(true);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            if (
                exception instanceof InterruptedException
            ) Thread.currentThread().interrupt();
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        }
    }

    private final class Events implements Flow.Subscriber<String> {

        private final BiConsumer<String, JsonNode> progress;
        private String type = "";
        private int characters;
        private JsonNode result;
        private BusinessException failure;
        private Flow.Subscription subscription;

        Events(BiConsumer<String, JsonNode> progress) {
            this.progress = progress;
        }

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        public void onNext(String line) {
            characters += line.length();
            if (characters > 15_000_000) {
                failure = new BusinessException(CompanionError.AI_UNAVAILABLE);
                subscription.cancel();
                return;
            }
            if (failure != null) return;
            try {
                if (line.startsWith("event: ")) type = line.substring(7);
                else if (line.startsWith("data: ")) {
                    var data = json.readTree(line.substring(6));
                    if ("status".equals(type)) progress.accept(type, data);
                    else if ("result".equals(type)) result = data;
                    else if ("error".equals(type)) failure =
                        new BusinessException(
                            "COMPANION_QUOTA_EXCEEDED".equals(
                                data.path("code").asString()
                            )
                                ? CompanionError.COMPANION_QUOTA_EXCEEDED
                                : CompanionError.AI_UNAVAILABLE
                        );
                }
            } catch (RuntimeException error) {
                failure = new BusinessException(CompanionError.AI_UNAVAILABLE);
            }
        }

        public void onError(Throwable error) {
            failure = new BusinessException(CompanionError.AI_UNAVAILABLE);
        }

        public void onComplete() {}
    }

    public String summarize(String previous, Object turns) {
        try {
            var request = HttpRequest.newBuilder(endpoint("summaries"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            Map.of("previous", previous, "turns", turns)
                        )
                    )
                )
                .build();
            var response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) return null;
            var result = json.readTree(response.body());
            if (!result.path("reviewed").asBoolean()) return null;
            String summary = result.path("summary").asString("");
            return summary.isBlank() || summary.length() > 3000
                ? null
                : summary;
        } catch (Exception exception) {
            if (
                exception instanceof InterruptedException
            ) Thread.currentThread().interrupt();
            return null;
        }
    }

    public JsonNode compute(String operation, Object body) {
        if (
            !java.util.Set.of(
                "consolidations",
                "dailies",
                "embeddings",
                "memory-reviews"
            ).contains(operation)
        ) throw new IllegalArgumentException("Unknown computation");
        try {
            var request = HttpRequest.newBuilder(endpoint(operation))
                .timeout(
                    Duration.ofSeconds("embeddings".equals(operation) ? 4 : 30)
                )
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body)
                    )
                )
                .build();
            var response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) throw new BusinessException(
                CompanionError.AI_UNAVAILABLE
            );
            return json.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        } catch (Exception exception) {
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        }
    }

    public byte[] draw(String instruction) {
        try {
            var request = HttpRequest.newBuilder(endpoint("drawings"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            Map.of("instruction", instruction)
                        )
                    )
                )
                .build();
            var response = http.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            );
            if (
                response.statusCode() != 200 ||
                response.body().length > 10_000_000
            ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        } catch (Exception exception) {
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        }
    }
}
