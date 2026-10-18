package com.pluchon.meng.api.companion.event;

// 升阶通知只携带展示快照，不包含聊天和记忆
public record StageReachedEvent(long userId, String characterCode, String characterName,
                                int stage, String stageName) {
    public static final String EXCHANGE = "meng.companion.topic";
    public static final String EVENT_TYPE = "companion.stage-reached";
}
