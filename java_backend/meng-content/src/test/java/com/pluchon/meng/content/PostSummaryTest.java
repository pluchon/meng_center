package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.content.service.PostSummary;
import org.junit.jupiter.api.Test;

class PostSummaryTest {

    @Test
    void stripsMarkdownAndLimitsCharacters() {
        var s = new PostSummary();
        assertEquals("你好 世界", s.summarize("# 你好\n\n**世界**"));
        assertEquals(120, s.summarize("萌".repeat(130)).codePointCount(0, 120));
    }
}
