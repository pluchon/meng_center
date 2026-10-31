package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

// 气泡语音存到仓库外，公开路径只按消息编号读取；合成失败时这条气泡只有文字
@Service
public class CompanionVoice {

    @Autowired
    private AiClient ai;

    @Autowired
    private CompanionMapper mapper;

    private final Path root;

    public CompanionVoice() {
        String configured = System.getenv("MENG_COMPANION_WORK_ROOT");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("Missing MENG_COMPANION_WORK_ROOT");
        root = Path.of(configured).toAbsolutePath().normalize().resolve("voice");
    }

    public record Saved(String name, int durationMs) {}

    // 只给计次的正常回复合成，成本跟着每日条数封顶；各气泡并行，结果和气泡一一对应，失败的位置为空
    public List<Saved> synthesize(JsonNode result) {
        var bubbles = result.path("bubbles");
        if (
            !result.path("billable").asBoolean() ||
            !Set.of("PASS", "ROMANCE_PUSH").contains(result.path("label").asString()) ||
            !bubbles.isArray() ||
            bubbles.size() > 3
        ) return List.of();
        var calls = new ArrayList<CompletableFuture<JsonNode>>();
        String mood = result.path("voiceMood").asString("AUTO");
        for (var bubble : bubbles) calls.add(
            bubble.isString() && !bubble.asString().isBlank() && bubble.asString().length() <= 500
                ? ai.voice(bubble.asString(), mood, calls.isEmpty())
                : CompletableFuture.completedFuture(null)
        );
        var saved = new ArrayList<Saved>();
        for (var call : calls) saved.add(save(call.join()));
        return saved;
    }

    private Saved save(JsonNode voice) {
        if (voice == null || !voice.path("audio").isString()) return null;
        try {
            Files.createDirectories(root);
            String name = UUID.randomUUID() + ".mp3";
            Files.write(path(name), Base64.getDecoder().decode(voice.path("audio").asString()));
            return new Saved(name, voice.path("durationMs").asInt());
        } catch (IOException | IllegalArgumentException error) {
            return null;
        }
    }

    public void discard(List<Saved> voices) {
        for (var voice : voices) {
            if (voice == null) continue;
            try {
                Files.deleteIfExists(path(voice.name()));
            } catch (IOException ignored) {}
        }
    }

    public Path read(long user, long message) {
        String name = mapper.voicePath(message, user);
        if (name == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return path(name);
    }

    private Path path(String name) {
        if (!name.matches("[a-f0-9-]{36}\\.mp3")) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return root.resolve(name);
    }
}
