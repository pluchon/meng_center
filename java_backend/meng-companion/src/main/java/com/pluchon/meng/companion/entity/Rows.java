package com.pluchon.meng.companion.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

// 萌萌独占库的持久化记录，不直接作为接口响应
public final class Rows {
    private Rows() {}
    @Data
    public static class Character {
        private String code;
        private String name;
        private String tagline;
        private String avatar;
        private String portrait;
        private String stageNames;
    }
    @Data
    public static class Bond {
        private Long id;
        private Long userId;
        private String characterCode;
        private int affection;
        private int stage;
        private long revision;
        private LocalDateTime firstChatTime;
        private LocalDateTime lastChatTime;
        private int chatDays;
        private int todayGain;
        private LocalDate todayDate;
    }
    @Data
    public static class Session {
        private Long id;
        private Long userId;
        private String characterCode;
        private String title;
        private String summary;
        private long summaryUntilMessageId;
        private int messageCount;
        private LocalDateTime lastMessageTime;
    }
    @Data
    public static class Message {
        private Long id;
        private Long sessionId;
        private String role;
        private String content;
        private String bubbles;
        private String imageIds;
        private Long workId;
        private int affectionDelta;
        private String safetyLabel;
        private LocalDateTime createTime;
        private boolean completed;
    }
    @Data
    public static class Memory {
        private Long id;
        private Long userId;
        private String characterCode;
        private String content;
        private String contentHash;
        private Long sourceMessageId;
        private LocalDateTime lastUsedTime;
    }
    @Data
    public static class Work {
        private Long id;
        private Long userId;
        private String characterCode;
        private Long sessionId;
        private String kind;
        private String title;
        private String body;
        private String filePath;
        private LocalDateTime createTime;
    }
}
