package com.pluchon.meng.api.user.event;

// 注册申请通过事件，审核事务提交后发出
public record UserApprovedEvent(long userId) {
    public static final String EVENT_TYPE = "user.approved";
    public static final String EXCHANGE = "meng.user.topic";
}
