package com.pluchon.meng.content.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import feign.Feign;
import feign.Request;
import feign.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

class AiClientTimeoutTest {

    @Test
    void requestArgumentsKeepMethodDeadlinesAcrossThreadsAndGlobalOptions() throws Exception {
        var observed = new ConcurrentHashMap<String, Integer>();
        var client = Feign.builder()
            .contract(new SpringMvcContract())
            .encoder((body, type, template) -> template.body("{}"))
            .decoder((response, type) ->
                type == byte[].class
                    ? new byte[] { 1 }
                    : new AiComputation.EmbeddingsResponse(List.of(List.of(.1f)), "fake")
            )
            .options(
                new Request.Options(
                    500,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    15000,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    false
                )
            )
            .client((request, options) -> {
                observed.put(request.url(), options.readTimeoutMillis());
                return Response.builder()
                    .request(request)
                    .status(200)
                    .headers(Map.of())
                    .body(new byte[] { 1 })
                    .build();
            })
            .target(AiClient.class, "http://test");
        var requestThread = Executors.newSingleThreadExecutor();
        try {
            requestThread
                .submit(() ->
                    client.covers(Map.of("title", "测试", "summary", "摘要", "category", "TECH"))
                )
                .get();
            requestThread.submit(() -> client.embeddings(Map.of("texts", List.of("测试")))).get();
            assertEquals(60000, observed.get("http://test/ai/internal/covers"));
            assertEquals(2000, observed.get("http://test/ai/internal/embeddings"));
        } finally {
            requestThread.shutdownNow();
        }
    }
}
