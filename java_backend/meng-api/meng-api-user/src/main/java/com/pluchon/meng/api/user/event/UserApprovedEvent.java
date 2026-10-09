package com.pluchon.meng.api.user.event;

public record UserApprovedEvent(long userId) {
    public static final String EVENT_TYPE = "user.approved";
    public static final String EXCHANGE = "meng.user.topic";
}
