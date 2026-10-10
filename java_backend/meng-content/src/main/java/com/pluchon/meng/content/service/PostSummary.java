package com.pluchon.meng.content.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

@Component
public class PostSummary {

    private final Parser parser = Parser.builder().build();
    private final TextContentRenderer renderer =
        TextContentRenderer.builder().build();

    public String summarize(String markdown) {
        String text = renderer
            .render(parser.parse(markdown))
            .replaceAll("\\s+", " ")
            .trim();
        int count = text.codePointCount(0, text.length());
        return text.substring(
            0,
            text.offsetByCodePoints(0, Math.min(120, count))
        );
    }
}
