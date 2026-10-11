package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.content.converter.SearchHighlight;
import com.pluchon.meng.content.service.HotRankService;
import com.pluchon.meng.content.service.PostSearchService;

import org.junit.jupiter.api.Test;

class SearchHighlightTest {

    @Test
    void preservesHtmlAndUnicodeAsText() {
        String text = "<img onerror=evil()>萌萌🌟喜欢萌萌";
        var segments = SearchHighlight.literal(text, "萌萌");
        assertEquals(text, SearchHighlight.text(segments));
        assertEquals(2, segments.stream().filter(s -> s.matched()).count());
        assertTrue(segments.get(0).text().contains("<img"));
    }

    @Test
    void parsesOnlyExactMarkersAndKeepsEscapedText() {
        var segments =
                SearchHighlight.marked("<em>原文</em> [start]萌萌🌟[end] 尾巴", "[start]", "[end]");
        assertEquals("<em>原文</em> 萌萌🌟 尾巴", SearchHighlight.text(segments));
        assertEquals(
                "萌萌🌟",
                segments.stream().filter(s -> s.matched()).findFirst().orElseThrow().text());
    }

    @Test
    void malformedMarkersDoNotDiscardUserText() {
        String text = "原文 [start]未知";
        assertEquals(text, SearchHighlight.text(SearchHighlight.marked(text, "[start]", "[end]")));
        assertFalse(SearchHighlight.marked(text, "[start]", "[end]").get(0).matched());
    }

    @Test
    void paddedIdsPreserveNumericTieOrder() {
        assertTrue(HotRankService.member(100).compareTo(HotRankService.member(99)) > 0);
        assertEquals(19, HotRankService.member(Long.MAX_VALUE).length());
    }

    @Test
    void rejectsInvalidQueriesBeforeAccessingReadModels() {
        var service = new PostSearchService();
        for (String q : java.util.List.of(" ", "🌟".repeat(51)))
            assertThrows(BusinessException.class, () -> service.search(q, null, null, 1, 5));
        assertThrows(BusinessException.class, () -> service.search("萌", null, null, 2001, 5));
    }
}
