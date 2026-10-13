package com.pluchon.meng.api.content.event;

public record CommentCreatedEvent(
    long commentId,
    long postId,
    String postTitle,
    long authorId,
    Long targetUserId,
    Long parentCommentId,
    String excerpt,
    String originalExcerpt
) {
    public static final String EXCHANGE = "meng.content.topic";
    public static final String EVENT_TYPE = "comment.created";
}
