package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// 角色内容独立于结构迁移，按版本原子导入；同一版本不允许静默改写。
@Service
public class CompanionCatalog implements ApplicationRunner {

    @Autowired
    private RuntimeMapper runtime;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();
    private static final Set<String> CODES = Set.of("NEKO");
    private static final Set<String> SCENES = Set.of(
        "CHAT",
        "PRAISE",
        "COMFORT",
        "REST",
        "GOODBYE",
        "GREETING"
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments arguments) throws Exception {
        var catalog = json.readTree(
            new ClassPathResource("companion/catalog.json").getContentAsString(
                StandardCharsets.UTF_8
            )
        );
        String version = catalog.path("version").asString();
        if (
            !version.matches("[a-zA-Z0-9._-]{1,80}")
        ) throw new IllegalArgumentException(
            "Invalid companion catalog version or rollout"
        );
        var seen = new HashSet<String>();
        for (var line : catalog.path("lines"))
            if (
                !CODES.contains(line.path("characterCode").asString())
            ) throw new IllegalArgumentException(
                "Unknown companion scene character"
            );
        for (var character : catalog.path("characters")) {
            String code = character.path("code").asString();
            if (
                !CODES.contains(code) || !seen.add(code)
            ) throw new IllegalArgumentException(
                "Invalid companion catalog character"
            );
            var card = (ObjectNode) character.path("card").deepCopy();
            if (
                card.path("name").asString().isBlank()
            ) throw new IllegalArgumentException("Incomplete companion card");
            for (String field : Set.of("profile","brief","voice","tics","kaomoji","knowledge","fallback","refusal","crisis","opening","form")) {
                if (!card.has(field)) throw new IllegalArgumentException("Incomplete companion snapshotRuntime card: "+field);
            }
            card.put("version", version);
            card.put("lineVersion", version);
            var lines = new LinkedHashMap<String, JsonNode>();
            for (var line : catalog.path("lines")) {
                if (
                    !code.equals(line.path("characterCode").asString())
                ) continue;
                String scene = line.path("scene").asString();
                var dialogue = line.path("dialogue");
                if (
                    !SCENES.contains(scene) ||
                    dialogue.path("user").asString().isBlank() ||
                    dialogue.path("character").asString().isBlank()
                ) throw new IllegalArgumentException(
                    "Invalid companion scene line"
                );
                if (
                    lines.put(key(scene, dialogue), line) != null
                ) throw new IllegalArgumentException(
                    "Duplicate companion scene line"
                );
            }
            if (lines.size()<3) throw new IllegalArgumentException("奶糖至少需要三条示例对白");
            runtime.lockCharacter(code);
            String existing = runtime.catalogCard(code, version);
            if (existing != null) {
                if (
                    !json.readTree(existing).equals(card) ||
                    !sameLines(lines, runtime.catalogLines(code, version))
                ) throw new IllegalStateException(
                    "Companion catalog changed without a new version: " +
                        code +
                        "/" +
                        version
                );
                // 运营已关闭或调整灰度的版本保持原状态，重启不能重新激活。
                runtime.retirePreviousCatalog(code, version);
                continue;
            }
            runtime.insertCatalogCard(code, version, card.toString());
            for (var line : lines.values())
                runtime.insertCatalogLine(
                    code,
                    line.path("scene").asString(),
                    line.path("dialogue").toString(),
                    version
                );
            runtime.activateCatalog(code, version);
            runtime.retirePreviousCatalog(code, version);
        }
        if (!seen.equals(CODES)) throw new IllegalArgumentException(
            "Companion catalog must include all characters"
        );
    }

    private boolean sameLines(
        Map<String, JsonNode> expected,
        java.util.List<Rows.Line> stored
    ) {
        if (expected.size() != stored.size()) return false;
        for (var line : stored) {
            var dialogue = json.readTree(line.getDialogue());
            var source = expected.get(
                key(line.getScene(), dialogue)
            );
            if (
                source == null || !source.path("dialogue").equals(dialogue)
            ) return false;
        }
        return true;
    }

    private static String key(
        String scene,
        JsonNode dialogue
    ) {
        return (
            scene +
            ":" +
            dialogue.path("user").asString()
        );
    }
}
