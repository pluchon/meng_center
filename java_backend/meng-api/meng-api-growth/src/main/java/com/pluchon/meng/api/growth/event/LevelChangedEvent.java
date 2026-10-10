package com.pluchon.meng.api.growth.event;

public record LevelChangedEvent(long userId, int level) {
    public static final String EXCHANGE = "meng.growth.topic";
    public static final String EVENT_TYPE = "growth.level-changed";
}
