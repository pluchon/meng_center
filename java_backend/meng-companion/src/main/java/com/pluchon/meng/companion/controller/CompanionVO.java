package com.pluchon.meng.companion.controller;

import java.time.LocalDateTime;
import java.util.List;

// 对外响应只包含会话和消息，不暴露记忆来源与文件路径。
public final class CompanionVO {
    private CompanionVO() {}
    public record Source(String type, Long postId, String title, String url, String site) {}
    public record Session(long id, String title, LocalDateTime lastMessageTime, String opening,
                          boolean unread, boolean generating) {}
    public record Message(long id, String role, String kind, String content, String sticker,
                          List<Long> imageIds, List<Source> sources, LocalDateTime createTime,
                          String clientMessageId, String turnState, Integer voiceMs) {}
    public record Messages(List<Message> records, Long nextBefore) {}
}
