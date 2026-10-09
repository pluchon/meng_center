package com.pluchon.meng.user.mq;

public final class UserMqNames {

    public static final String CODE_QUEUE = "meng.user.code-delivery";
    public static final String NOTIFICATION_QUEUE = "meng.user.review-notification";
    public static final String DEAD_QUEUE = "meng.user.delivery.dead";
    public static final String INTERNAL_EXCHANGE = "meng.user.delivery.direct";
    public static final String CODE_ROUTE = "code";
    public static final String NOTIFICATION_ROUTE = "review";
    public static final String DEAD_ROUTE = "dead";
    public static final String DELIVERY_OK = "DELIVERED";
    public static final String DELIVERY_FAILED = "FAILED";
    public static final String PROVIDER_OK = "OK";

    private UserMqNames() {}
}
