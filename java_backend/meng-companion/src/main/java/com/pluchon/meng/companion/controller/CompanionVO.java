package com.pluchon.meng.companion.controller;

import java.time.LocalDateTime;
import java.util.List;

// 对外投影不暴露好感数字、记忆来源和磁盘路径
public final class CompanionVO {

    private CompanionVO() {}

    public record Character(
        String code,
        String name,
        String tagline,
        String avatar,
        String portrait,
        int stage,
        String stageName,
        double progress,
        String lastMessage,
        long acquaintanceDays,
        int chatDays,
        List<String> stageNames,
        LocalDateTime lastMessageTime,
        int level,
        List<String> levelNames,
        String status
    ) {}

    public record Session(
        long id,
        String characterCode,
        String title,
        LocalDateTime lastMessageTime,
        String opening,
        boolean unread,
        boolean generating,
        boolean drawing
    ) {}

    public record Message(
        long id,
        String role,
        String content,
        List<Long> imageIds,
        Long workId,
        LocalDateTime createTime,
        String kind,
        String sticker,
        String clientMessageId,
        String turnState,
        boolean canRegenerate,
        boolean retracted,
        String reaction,
        String typing,
        Integer retractAfterMs,
        List<java.util.Map<String,String>> sources
    ) {}

    public record Messages(List<Message> records, Long nextBefore) {}

    public record Memory(
        long id,
        String kind,
        String content,
        boolean pinned,
        java.time.LocalDate dueDate
    ) {}

    public record Work(
        long id,
        String characterCode,
        long sessionId,
        String kind,
        String title,
        String body,
        String fileUrl,
        LocalDateTime createTime,
        Long parentId
    ) {}
}
