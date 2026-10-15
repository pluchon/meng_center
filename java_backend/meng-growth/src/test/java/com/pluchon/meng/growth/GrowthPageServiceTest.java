package com.pluchon.meng.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.entity.DailyExperience;
import com.pluchon.meng.growth.entity.DailyPoints;
import com.pluchon.meng.growth.entity.ExperienceRecordRow;
import com.pluchon.meng.growth.entity.SourceExperience;
import com.pluchon.meng.growth.entity.SourcePoints;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.entity.enums.PointSource;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import com.pluchon.meng.growth.mapper.GrowthPageMapper;
import com.pluchon.meng.growth.service.GrowthPageService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GrowthPageServiceTest {
    GrowthPageMapper page = mock(GrowthPageMapper.class);
    GrowthMapper mapper = mock(GrowthMapper.class);
    GrowthPageService service = new GrowthPageService();
    LocalDate today = LocalDate.of(2026, 10, 12);

    @BeforeEach
    void setup() {
        UserContext.bind(new RequestIdentity(7L, "USER", "growth-test"));
        ReflectionTestUtils.setField(service, "pageMapper", page);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "properties", new GrowthProperties("Asia/Taipei", 5, 10, 2, 3, 10, List.of(0L, 50L, 150L), 5, 10, 2));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-11T16:00:01Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void monthValidationAndStreakIncludeYesterdayWithoutSigningToday() {
        when(mapper.selectCheckInDates(eq(7L), any(), any())).thenReturn(List.of(today.minusDays(1)));
        when(page.selectEarlierCheckIns(eq(7L), any(), anyInt()))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(5)));
        var result = service.checkIns(null);
        assertEquals("2026-10", result.month());
        assertEquals(today, result.today());
        assertEquals(3, result.streak());
        assertEquals(1, result.count());
        assertEquals(5, result.reward());
        verify(mapper).selectCheckInDates(7, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        for (var month : List.of("2026-11", "2026-1", "2026-13", "", "invalid")) {
            assertThrows(BusinessException.class, () -> service.checkIns(month));
        }
        service.checkIns("2024-02");
        verify(mapper).selectCheckInDates(7, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
    }

    @Test
    void streakUsesDateCursorBeyondOneBatchAndStopsAtFirstGap() {
        var dates = IntStream.range(0, 128).mapToObj(today::minusDays).toList();
        when(mapper.selectCheckInDates(eq(7L), any(), any())).thenReturn(List.of());
        when(page.selectEarlierCheckIns(7, today.plusDays(1), 128)).thenReturn(dates);
        when(page.selectEarlierCheckIns(7, today.minusDays(127), 128)).thenReturn(List.of(today.minusDays(128), today.minusDays(130)));
        assertEquals(129, service.checkIns("2025-01").streak());
        when(page.selectEarlierCheckIns(7, today.plusDays(1), 128)).thenReturn(List.of(today.minusDays(2)));
        assertEquals(0, service.checkIns(null).streak());
    }

    @Test
    void overviewUsesConfiguredThresholdsAndDistinctActiveDays() {
        when(page.selectTaskExperience(7, today)).thenReturn(List.of(new SourceExperience(ExpSource.POST, 3, 30, 8)));
        var result = service.overview();
        assertEquals(List.of(0L, 50L, 150L), result.levels().stream().map(level -> level.requiredExp()).toList());
        var post = result.tasks().stream().filter(task -> task.source() == ExpSource.POST).findFirst().orElseThrow();
        assertEquals(3, post.dailyMax());
        assertEquals(3, post.count());
        assertEquals(30, post.earned());
        assertEquals(8, post.activeDays());
        assertEquals(0, result.tasks().get(0).earned());
        verify(mapper, never()).insertAccountIfAbsent(7);
    }

    @Test
    void statsUseBusinessDayAndFillAllDatesWithMatchingSourceTotals() {
        var start = today.minusDays(6);
        when(page.selectDailyExperience(7, start, today.plusDays(1))).thenReturn(List.of(new DailyExperience(today, 12)));
        when(page.selectSourceExperience(7, start, today.plusDays(1))).thenReturn(List.of(
                new SourceExperience(ExpSource.POST, 1, 10, 1), new SourceExperience(ExpSource.COMMENT, 1, 2, 1)));
        var result = service.stats(7);
        assertEquals(7, result.days().size());
        assertEquals(start, result.days().get(0).date());
        assertEquals(today, result.days().get(6).date());
        assertEquals(0, result.days().get(0).amount());
        assertEquals(result.days().stream().mapToLong(day -> day.amount()).sum(), result.sources().stream().mapToLong(source -> source.amount()).sum());
        assertThrows(BusinessException.class, () -> service.stats(8));
    }

    @Test
    void pointStatsFillAllDatesAndKeepSourceTotalsOnTheSameRange() {
        var start = today.minusDays(29);
        when(page.selectDailyPoints(7, start, today.plusDays(1))).thenReturn(List.of(new DailyPoints(today, 17)));
        when(page.selectSourcePoints(7, start, today.plusDays(1))).thenReturn(List.of(
                new SourcePoints(PointSource.POST, 10), new SourcePoints(PointSource.COMMENT, 7)));
        var result = service.pointStats(30);
        assertEquals(30, result.days().size());
        assertEquals(start, result.days().get(0).date());
        assertEquals(17, result.days().get(29).amount());
        assertEquals(0, result.days().get(0).amount());
        assertEquals(3, result.sources().size());
        assertEquals(0, result.sources().get(0).amount());
        assertEquals(17, result.sources().stream().mapToLong(source -> source.amount()).sum());
        assertThrows(BusinessException.class, () -> service.pointStats(8));
    }

    @Test
    void recordsFilterByCurrentUserAndConvertUtcTimeToBusinessOffset() {
        when(page.countExperienceRecords(7, ExpSource.POST)).thenReturn(20L);
        when(page.selectExperienceRecords(7, ExpSource.POST, 12, 12))
                .thenReturn(List.of(new ExperienceRecordRow(ExpSource.POST, 10, LocalDateTime.of(2026, 10, 11, 16, 1))));
        var result = service.records(ExpSource.POST, 2, 12);
        assertEquals(20, result.total());
        assertEquals(today, result.records().get(0).time().toLocalDate());
        assertEquals(ZoneOffset.ofHours(8), result.records().get(0).time().getOffset());
        assertEquals(ExpSource.POST, result.records().get(0).source());
        assertEquals(10, result.records().get(0).amount());
        assertThrows(BusinessException.class, () -> service.records(null, 0, 12));
        assertThrows(BusinessException.class, () -> service.records(null, 1, 51));
        assertThrows(BusinessException.class, () -> service.records(null, Long.MAX_VALUE, 50));
        assertEquals(List.of(), service.records(ExpSource.POST, 10, 12).records());
        verify(page, never()).selectExperienceRecords(7, ExpSource.POST, 108, 12);
    }
}
