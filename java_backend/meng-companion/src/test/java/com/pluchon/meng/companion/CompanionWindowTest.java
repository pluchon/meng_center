package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import com.pluchon.meng.companion.service.CompanionBackground;
import com.pluchon.meng.companion.service.ReplyService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class CompanionWindowTest {

    @ParameterizedTest
    @CsvSource({ "1799,false,false", "1800,false,true", "0,true,true" })
    void compactionUsesIdleTimeOrFullRequestPressure(
        long idleSeconds,
        boolean pressure,
        boolean expected
    ) {
        var mapper = mock(CompanionMapper.class);
        var runtime = mock(RuntimeMapper.class);
        var ai = mock(AiClient.class);
        var background = new CompanionBackground();
        var now = Instant.parse("2026-10-27T01:00:00Z");
        ReflectionTestUtils.setField(background, "mapper", mapper);
        ReflectionTestUtils.setField(background, "runtime", runtime);
        ReflectionTestUtils.setField(background, "ai", ai);
        ReflectionTestUtils.setField(
            background,
            "clock",
            Clock.fixed(now, ZoneOffset.UTC)
        );
        var session = new Rows.Session();
        session.setId(1L);
        session.setLastMessageTime(
            LocalDateTime.ofInstant(
                now.minusSeconds(idleSeconds),
                ZoneOffset.UTC
            )
        );
        when(mapper.session(1L, 42L)).thenReturn(session);
        var history = new ArrayList<Rows.Message>();
        for (int i = 0; i < 20; i++) {
            var message = new Rows.Message();
            message.setId((long) i + 1);
            message.setRole(i % 2 == 0 ? "USER" : "CHARACTER");
            message.setContent("甲".repeat(1000));
            history.add(message);
        }
        history
            .get(history.size() - 1)
            .setMetrics(
                "{\"windowStartMessageId\":0,\"layers\":{\"contextBudget\":{\"compactAfterRound\":" +
                    pressure +
                    "}}}"
            );
        when(runtime.window(1L, 0L)).thenReturn(history);
        // 摘要失败时不推进窗口；本测试只核对触发时机和传入的完整原文。
        background.summarize(42L, 1L, 0L);
        if (expected) {
            var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(ai).summarize(any(), payload.capture());
            var turns = (List<?>) payload.getValue();
            assertEquals(pressure ? 10 : 12, turns.size());
            verify(mapper, never()).summary(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong()
            );
        } else verifyNoInteractions(ai);
    }

    @Test
    void consecutiveTurnsNeverDiscardOriginalTextAtThePerMessageBoundary() {
        var first = new Rows.Message();
        first.setRole("USER");
        first.setContent("甲".repeat(6000));
        var second = new Rows.Message();
        second.setRole("USER");
        second.setContent("保留尾部原文");
        var turns = ReplyService.turns(List.of(first, second));
        assertEquals(2, turns.size());
        assertEquals(
            first.getContent() + second.getContent(),
            turns.get(0).get("content") + turns.get(1).get("content")
        );
    }
}
