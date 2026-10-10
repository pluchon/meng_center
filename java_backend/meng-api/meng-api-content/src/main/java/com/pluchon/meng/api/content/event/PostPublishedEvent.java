package com.pluchon.meng.api.content.event;

public record PostPublishedEvent(long postId, long authorId) {
    public static final String EXCHANGE = "meng.content.topic";
    public static final String EVENT_TYPE = "post.published";
}
