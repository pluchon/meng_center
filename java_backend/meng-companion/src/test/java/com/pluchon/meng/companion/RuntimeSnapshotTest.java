package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.*;
import com.pluchon.meng.companion.service.CompanionMemoryCache;
import com.pluchon.meng.companion.service.CompanionRuntime;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class RuntimeSnapshotTest {

    @Test
    void snapshotStaysStableUntilRememberedFactChanges() {
        var service = new CompanionRuntime();
        var mapper = mock(CompanionMapper.class);
        var state = mock(RuntimeMapper.class);
        var cache = mock(CompanionMemoryCache.class);
        when(cache.get(42, "NEKO")).thenAnswer(call ->
            mapper.memories(42, "NEKO")
        );
        ReflectionTestUtils.setField(service, "memoryCache", cache);
        ReflectionTestUtils.setField(service, "state", state);
        ReflectionTestUtils.setField(
            service,
            "clock",
            Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC)
        );
        var session = new Rows.Session();
        session.setId(1L);
        session.setUserId(42L);
        session.setCharacterCode("NEKO");
        var bond = new Rows.Bond();
        bond.setProfile("原档案");
        var memory = new Rows.Memory();
        memory.setId(10L);
        memory.setContent("喜欢蓝色");
        memory.setPinned(true);
        when(mapper.memories(42, "NEKO")).thenReturn(List.of(memory));
        var first = service.snapshot(session, bond, false);
        bond.setProfile("后台新档案");
        assertEquals(
            first.toString(),
            service.snapshot(session, bond, false).toString()
        );
        verify(state, times(1)).snapshot(anyString(), any(), eq(1L), eq(42L));
        when(mapper.memories(42, "NEKO")).thenReturn(List.of());
        var refreshed = service.snapshot(session, bond, false);
        assertEquals(0, refreshed.path("memories").size());
        assertEquals("后台新档案", refreshed.path("profile").asString());
        verify(state, times(2)).snapshot(anyString(), any(), eq(1L), eq(42L));
    }

    @Test
    void sourcesOnlyAcceptSitePosts() {
        var service = new CompanionRuntime();
        var json = JsonMapper.builder().build();
        assertThrows(IllegalArgumentException.class, () ->
            service.sources(
                json.readTree("[{\"postId\":0,\"title\":\"无效\"}]")
            )
        );
        assertEquals(
            2L,
            service
                .sources(
                    json.readTree("[{\"postId\":2,\"title\":\"配色记录\"}]")
                )
                .get(0)
                .get("postId")
        );
    }
}
