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
        private int level = 2;
        private boolean levelChanged;
        private String profile = "";
        private long profileRevision;
        private String timeZone = "Asia/Taipei";
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
        private long windowStartMessageId;
        private boolean unread;
        private boolean generating;
        private boolean drawing;
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
        private String clientMessageId;
        private Long turnId;
        private String turnState;
        private String kind = "TEXT";
        private String sticker;
        private String feeling;
        private String intent;
        private String promptVersion;
        private String model;
        private String memoryIds;
        private String metrics;
        private Long requestWorkId;
        private Long regeneratesMessageId;
        private long requestRevision;
        private String timeZone = "Asia/Taipei";
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
        private String kind = "LIFE";
        private String keywords;
        private int importance = 5;
        private String embedding;
        private boolean pinned;
        private LocalDateTime invalidAt;
        private Long supersededBy;
        private LocalDate dueDate;
        private boolean done;
        private Long sourceSessionId;
        private LocalDateTime validFrom;
        private LocalDateTime mentionedAt;
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
        private Long parentId;
    }
}
