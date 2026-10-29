package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.converter.CompanionSources;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 快照保存原文，记忆失效和用户编辑后刷新，热聊期间保持前缀不变。
@Service
public class CompanionRuntime {

    @Autowired
    private RuntimeMapper state;

    @Autowired
    private Clock clock;

    @Autowired
    private CompanionMemoryCache memoryCache;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public JsonNode snapshot(
        Rows.Session session,
        Rows.Bond bond,
        boolean force
    ) {
        var all = memoryCache.get(
            session.getUserId(),
            session.getCharacterCode()
        );
        var current = new LinkedHashMap<Long, Rows.Memory>();
        all.forEach(m -> current.put(m.getId(), m));
        JsonNode previous =
            session.getContextSnapshot() == null
                ? null
                : json.readTree(session.getContextSnapshot());
        boolean refresh =
            force ||
            previous == null ||
            session.getSnapshotTime() == null ||
            !session
                .getSnapshotTime()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of(bond.getTimeZone()))
                .toLocalDate()
                .equals(
                    clock
                        .instant()
                        .atZone(ZoneId.of(bond.getTimeZone()))
                        .toLocalDate()
                );
        if (previous != null) {
            for (var old : previous.path("memories")) {
                var now = current.get(old.path("id").asLong());
                if (
                    now == null ||
                    !now.getContent().equals(old.path("content").asString()) ||
                    now.isPinned() != old.path("pinned").asBoolean()
                ) refresh = true;
            }
        }
        if (!refresh) return previous;
        var memories = new ArrayList<Map<String, Object>>();
        for (var m : all) {
            if (!m.isPinned() && !"SELF".equals(m.getKind())) continue;
            var row = new LinkedHashMap<String, Object>();
            row.put("id", m.getId());
            row.put("kind", m.getKind());
            row.put("content", m.getContent());
            row.put("pinned", m.isPinned());
            row.put("validFrom", m.getValidFrom());
            memories.add(row);
        }
        var value = json.valueToTree(
            Map.of(
                "profile",
                Objects.requireNonNullElse(bond.getProfile(), ""),
                "memories",
                memories
            )
        );
        state.snapshot(
            value.toString(),
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
            session.getId(),
            session.getUserId()
        );
        session.setContextSnapshot(value.toString());
        session.setSnapshotTime(
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        return value;
    }

    public void invalidate(long user, String code) {
        state.invalidateSnapshots(user, code);
    }

    public void metadata(Rows.Message message, JsonNode result) {
        message.setSources(
            json.writeValueAsString(sources(result.path("sources")))
        );
        state.metadata(message.getSources(), message.getId());
    }

    public List<Map<String, Object>> sources(JsonNode nodes) {
        var result = new ArrayList<Map<String, Object>>();
        for (var source : CompanionSources.read(nodes)) {
            result.add("POST".equals(source.type())
                ? Map.of("type", "POST", "postId", source.postId(), "title", source.title())
                : Map.of("type", "WEB", "title", source.title(), "url", source.url(), "site", source.site()));
        }
        return result;
    }

    public static int tokens(String value) {
        if (value == null) return 0;
        double size = 0;
        for (int c : value.codePoints().toArray()) size += c > 127 ? 1 : 0.25;
        return (int) Math.ceil(size);
    }
}
