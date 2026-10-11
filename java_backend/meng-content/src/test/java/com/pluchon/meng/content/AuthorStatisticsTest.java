package com.pluchon.meng.content;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.mapper.AuthorDailyStatMapper;
import com.pluchon.meng.content.service.AuthorStatisticsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthorStatisticsTest {

    private AuthorStatisticsService service(
        String time,
        AuthorDailyStatMapper mapper
    ) {
        var service = new AuthorStatisticsService();
        ReflectionTestUtils.setField(
            service,
            "clock",
            Clock.fixed(Instant.parse(time), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "stats", mapper);
        return service;
    }

    @Test
    void taipeiMidnightSplitsAdjacentSecondsIntoTwoDays() {
        var mapper = mock(AuthorDailyStatMapper.class);
        service("2026-10-11T15:59:59Z", mapper).record(1, CreatorMetric.VIEW);
        service("2026-10-11T16:00:00Z", mapper).record(1, CreatorMetric.VIEW);
        verify(mapper).incrementCount(
            1,
            LocalDate.of(2026, 10, 11),
            "view_count",
            1
        );
        verify(mapper).incrementCount(
            1,
            LocalDate.of(2026, 10, 12),
            "view_count",
            1
        );
    }

    @Test
    void onlySameTaipeiDayRelationshipsAreDecremented() {
        var mapper = mock(AuthorDailyStatMapper.class);
        var service = service("2026-10-11T16:00:01Z", mapper);
        service.cancel(
            1,
            CreatorMetric.LIKE,
            LocalDateTime.of(2026, 10, 11, 16, 0)
        );
        verify(mapper).incrementCount(
            1,
            LocalDate.of(2026, 10, 12),
            "like_count",
            -1
        );
    }

    @Test
    void historicAndMissingRelationshipsDoNotRewriteStatistics() {
        var mapper = mock(AuthorDailyStatMapper.class);
        var service = service("2026-10-11T16:00:01Z", mapper);
        service.cancel(
            1,
            CreatorMetric.FAVORITE,
            LocalDateTime.of(2026, 10, 11, 15, 59, 59)
        );
        service.cancel(1, CreatorMetric.FAVORITE, null);
        verifyNoInteractions(mapper);
    }
}
