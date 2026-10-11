package com.pluchon.meng.content.converter;

import com.pluchon.meng.content.controller.vo.HighlightSegmentVO;

import java.util.ArrayList;
import java.util.List;

// 搜索高亮片段解析与安全文本组装
public final class SearchHighlight {

    private SearchHighlight() {}

    public static List<HighlightSegmentVO> literal(String text, String word) {
        var out = new ArrayList<HighlightSegmentVO>();
        int cursor = 0;
        int index;
        if (word.isEmpty()) return List.of(new HighlightSegmentVO(text, false));
        while ((index = text.indexOf(word, cursor)) >= 0) {
            if (index > cursor)
                out.add(new HighlightSegmentVO(text.substring(cursor, index), false));
            out.add(new HighlightSegmentVO(text.substring(index, index + word.length()), true));
            cursor = index + word.length();
        }
        if (cursor < text.length() || out.isEmpty())
            out.add(new HighlightSegmentVO(text.substring(cursor), false));
        return List.copyOf(out);
    }

    public static List<HighlightSegmentVO> marked(String text, String before, String after) {
        var out = new ArrayList<HighlightSegmentVO>();
        int cursor = 0;
        int index;
        while ((index = text.indexOf(before, cursor)) >= 0) {
            var end = text.indexOf(after, index + before.length());
            if (end < 0) return List.of(new HighlightSegmentVO(text, false));
            if (index > cursor)
                out.add(new HighlightSegmentVO(text.substring(cursor, index), false));
            out.add(new HighlightSegmentVO(text.substring(index + before.length(), end), true));
            cursor = end + after.length();
        }
        if (cursor < text.length() || out.isEmpty())
            out.add(new HighlightSegmentVO(text.substring(cursor), false));
        return List.copyOf(out);
    }

    public static String text(List<HighlightSegmentVO> segments) {
        return segments.stream()
                .map(HighlightSegmentVO::text)
                .collect(java.util.stream.Collectors.joining());
    }
}
