package com.pluchon.meng.api.content.event;

// 喜欢与收藏事件，快照字段使消费者不必回查内容域
public record ContentInteractionEvent(
    String eventType,
    long postId,
    String postTitle,
    Long commentId,
    String excerpt,
    long actorId,
    long authorId
) {
    public static final String EXCHANGE = "meng.content.topic";
}
