package com.pluchon.meng.companion.entity;

import java.time.LocalDateTime;
import lombok.Data;

// 奶糖独占库的持久化记录，不直接作为接口响应
public final class Rows {

    @Data
    public static class Line {

        private Long id;
        private String characterCode;
        private String scene;
        private String dialogue;
    }

    private Rows() {}

    @Data
    public static class Bond {

        private Long id;
        private Long userId;
        private String characterCode;
        private long revision;
        private String profile = "";
        private long profileRevision;
        private String timeZone = "Asia/Taipei";
    }

    @Data
    public static class Session {

        private Long id;
        private Long userId;
        private String characterCode;
        private String title;
        private String summary;
        private String contextSnapshot;
        private LocalDateTime snapshotTime;
        private long summaryUntilMessageId;
        private int messageCount;
        private long windowStartMessageId;
        private boolean unread;
        private boolean generating;
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
        private String safetyLabel;
        private LocalDateTime createTime;
        private boolean completed;
        private String clientMessageId;
        private Long turnId;
        private String turnState;
        private String kind = "TEXT";
        private String sticker;
        private String sources;
        private String promptVersion;
        private String model;
        private String memoryIds;
        private String metrics;
        private long requestRevision;
        private String timeZone = "Asia/Taipei";
        private String voicePath;
        private Integer voiceMs;
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
        private String embeddingVersion;
        private boolean pinned;
        private LocalDateTime invalidAt;
        private Long supersededBy;
        private Long sourceSessionId;
        private LocalDateTime validFrom;
    }

}
