package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.entity.AuthorDailyStatEntity;
import com.pluchon.meng.content.entity.AuthorPublicationCount;
import com.pluchon.meng.content.mapper.AuthorDailyStatMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.service.CreatorService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CreatorTrendTest {

    @Test
    void fillsMissingDatesAndSeparatesDailyFromCumulativeCounts() {
        var service = new CreatorService();
        var stats = mock(AuthorDailyStatMapper.class);
        var posts = mock(PostMapper.class);
        var today = LocalDate.of(2026, 10, 12);
        var stat = new AuthorDailyStatEntity();
        stat.setStatDate(today.minusDays(1));
        stat.setViewCount(9L);
        when(stats.selectList(any())).thenReturn(List.of(stat));
        when(
            posts.selectPublicationCounts(eq(12L), any(), any(), anyInt())
        ).thenReturn(List.of(new AuthorPublicationCount(today, 1)));
        ReflectionTestUtils.setField(service, "stats", stats);
        ReflectionTestUtils.setField(service, "posts", posts);
        ReflectionTestUtils.setField(
            service,
            "clock",
            Clock.fixed(Instant.parse("2026-10-11T16:00:00Z"), ZoneOffset.UTC)
        );
        UserContext.bind(new RequestIdentity(12, "USER", "test"));
        try {
            for (int days : List.of(7, 30)) {
                var trend = service.trend(days);
                assertEquals(days, trend.size());
                for (int i = 0; i < days; i++) assertEquals(
                    today.minusDays(days - 1L).plusDays(i),
                    trend.get(i).date()
                );
                assertEquals(9, trend.get(days - 2).viewCount());
                assertEquals(0, trend.get(days - 1).viewCount());
                assertEquals(1, trend.get(days - 1).postCount());
                assertEquals(0, trend.get(0).favoriteCount());
            }
            assertThrows(BusinessException.class, () -> service.trend(8));
        } finally {
            UserContext.clear();
        }
    }
}
