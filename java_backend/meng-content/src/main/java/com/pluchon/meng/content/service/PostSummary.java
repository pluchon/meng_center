package com.pluchon.meng.content.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

// 正文纯文本提取和摘要生成
@Component
public class PostSummary {

    // 正文标记解析器
    private final Parser parser = Parser.builder().build();

    // 正文纯文本渲染器
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    // 移除正文标记并返回可检索的纯文本
    public String plain(String markdown) {
        return renderer.render(parser.parse(markdown)).replaceAll("\\s+", " ").trim();
    }

    // 截取码点上限内的正文摘要
    public String summarize(String markdown) {
        String text = plain(markdown);
        int count = text.codePointCount(0, text.length());
        return text.substring(0, text.offsetByCodePoints(0, Math.min(120, count)));
    }
}
