package com.pluchon.meng.growth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.*;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.entity.GrowthAccountEntity;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import com.pluchon.meng.growth.service.GrowthService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;

class GrowthServiceTest {

    GrowthMapper mapper;
    ApplicationEventPublisher events;
    GrowthService service;
    GrowthAccountEntity a;

    @BeforeEach
    void setup() {
        mapper = mock(GrowthMapper.class);
        events = mock(ApplicationEventPublisher.class);
        a = new GrowthAccountEntity();
        a.setId(1L);
        a.setUserId(7L);
        a.setExp(0L);
        a.setLevel(1);
        when(mapper.lock(7)).thenReturn(a);
        when(mapper.week(eq(7L), any(), any())).thenReturn(List.of());
        service = new GrowthService(
            mapper,
            new GrowthProperties(
                "Asia/Taipei",
                5,
                10,
                2,
                3,
                10,
                List.of(0L, 50L, 150L, 300L)
            ),
            events,
            Clock.fixed(Instant.parse("2026-10-11T16:00:00Z"), ZoneOffset.UTC)
        );
        UserContext.bind(new RequestIdentity(7, "USER", "test"));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void lazyAccountAndWeekStartsMonday() {
        var view = service.me();
        assertEquals(1, view.level());
        assertEquals(0, view.exp());
        assertEquals(LocalDate.parse("2026-10-12"), view.today());
        assertEquals(view.today(), view.week().get(0).date());
        assertEquals(view.today().plusDays(6), view.week().get(6).date());
        verify(mapper).ensure(7);
    }

    @Test
    void signInAndDuplicate() {
        when(mapper.checkIn(7, LocalDate.parse("2026-10-12"))).thenReturn(1, 0);
        assertEquals(5, service.checkIn().exp());
        assertThrows(BusinessException.class, () -> service.checkIn());
        verify(mapper, times(1)).record(
            7,
            "CHECK_IN",
            "2026-10-12",
            5,
            LocalDate.parse("2026-10-12")
        );
    }

    @Test
    void duplicateEventDoesNotReward() {
        when(mapper.exists(7, "POST", "5")).thenReturn(1);
        service.earned(7, ExpSource.POST, "5", Instant.now());
        verify(mapper, never()).record(
            anyLong(),
            anyString(),
            anyString(),
            anyInt(),
            any()
        );
    }

    @Test
    void dailyLimitUsesEventDate() {
        var instant = Instant.parse("2026-10-11T15:59:59Z");
        when(mapper.count(7, "POST", LocalDate.parse("2026-10-11"))).thenReturn(
            3
        );
        service.earned(7, ExpSource.POST, "5", instant);
        verify(mapper, never()).record(
            anyLong(),
            anyString(),
            anyString(),
            anyInt(),
            any()
        );
    }

    @Test
    void commentLimit() {
        when(mapper.count(eq(7L), eq("COMMENT"), any())).thenReturn(10);
        service.earned(7, ExpSource.COMMENT, "1", Instant.now());
        verify(mapper, never()).update(anyLong(), anyLong(), anyInt());
    }

    @Test
    void oneEventCanCrossMultipleLevels() {
        a.setExp(145L);
        service.earned(7, ExpSource.POST, "5", Instant.now());
        assertEquals(3, a.getLevel());
        verify(events, times(1)).publishEvent(new LevelChangedEvent(7, 3));
    }

    @Test
    void raisedThresholdNeverLowersLevel() {
        a.setLevel(4);
        service.earned(7, ExpSource.POST, "5", Instant.now());
        assertEquals(4, a.getLevel());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void maxLevelStillEarns() {
        a.setLevel(4);
        a.setExp(300L);
        service.earned(7, ExpSource.POST, "5", Instant.now());
        assertEquals(310, a.getExp());
        assertNull(service.me().nextLevelExp());
    }

    @Test
    void unauthenticatedCannotChooseAnotherAccount() {
        UserContext.clear();
        assertThrows(BusinessException.class, () -> service.me());
        verify(mapper, never()).ensure(anyLong());
    }
}
