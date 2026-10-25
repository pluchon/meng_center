package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import com.pluchon.meng.companion.service.CompanionContext;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class CompanionRetrievalTest {

    @ParameterizedTest
    @ValueSource(strings = { "[1]", "[]", "[99]", "[1,1]", "{}" })
    void memoryCandidatesAreNotInjectedBeforeRecall(String ranked) {
        var context = new CompanionContext();
        var mapper = mock(CompanionMapper.class);
        var runtime = mock(RuntimeMapper.class);
        var ai = mock(AiClient.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var json = JsonMapper.builder().findAndAddModules().build();
        var memories = new ArrayList<Rows.Memory>();
        for (int i = 1; i <= 16; i++) {
            var memory = new Rows.Memory();
            memory.setId((long) i);
            memory.setKind("LIFE");
            memory.setContent("学习记录" + i + "甲".repeat(50));
            memory.setImportance(5);
            memories.add(memory);
        }
        when(mapper.memories(42, "NEKO")).thenReturn(memories);
        when(runtime.recallEpisodes(anyLong(), anyString())).thenReturn(
            List.of()
        );
        when(runtime.recallMessages(anyLong(), anyString())).thenReturn(
            new ArrayList<>()
        );
        when(ai.compute(eq("embeddings"), any())).thenAnswer(call -> {
            Map<?, ?> data = call.getArgument(1);
            if (data.containsKey("candidates")) return json.readTree(
                "{\"ranked\":" + ranked + ",\"tokens\":12}"
            );
            return json.valueToTree(
                Map.of(
                    "vectors",
                    List.of(Collections.nCopies(1024, 0.1)),
                    "tokens",
                    2
                )
            );
        });
        ReflectionTestUtils.setField(context, "mapper", mapper);
        ReflectionTestUtils.setField(context, "runtime", runtime);
        ReflectionTestUtils.setField(context, "ai", ai);
        ReflectionTestUtils.setField(context, "redis", redis);
        ReflectionTestUtils.setField(context, "clock", Clock.systemUTC());
        var result = context.memories(
            42,
            "NEKO",
            "A query without shared words",
            "Asia/Taipei",
            "fixture"
        );
        assertEquals(15, result.recalled().size());
        org.mockito.Mockito.verifyNoInteractions(ai);
        assertEquals(List.of(), result.used());
        assertEquals(15,result.recalled().size());
        assertEquals(List.of(),result.items());
        org.mockito.Mockito.verifyNoInteractions(ai);

    }
}
