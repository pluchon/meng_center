package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.mapper.PostMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class PostRebuildBatchTest {
    @Test
    @SuppressWarnings("unchecked")
    void maintenancePageOverridesPublicPageLimitAndSkipsCounting() {
        var mapper = mock(PostMapper.class, CALLS_REAL_METHODS);
        doAnswer(
                        invocation -> {
                            Page<PostEntity> page = invocation.getArgument(0);
                            assertEquals(1L, page.getCurrent());
                            assertEquals(500L, page.getSize());
                            assertEquals(500L, page.maxLimit());
                            assertFalse(page.searchCount());
                            return page.setRecords(List.of());
                        })
                .when(mapper)
                .selectPage(any(Page.class), any());
        assertEquals(List.of(), mapper.selectRebuildBatch(9, LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertEquals(List.of(), mapper.selectRebuildBatch(9, null));
    }
}
