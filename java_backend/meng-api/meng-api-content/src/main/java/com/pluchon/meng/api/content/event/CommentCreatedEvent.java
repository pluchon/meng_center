package com.pluchon.meng.api.content.event;

import java.util.List;

public record CommentCreatedEvent(
    long commentId,
    long postId,
    String postTitle,
    long authorId,
    Long targetUserId,
    Long parentCommentId,
    String excerpt,
    String originalExcerpt,
    // 正文里 @ 到的用户，旧消息里没有这个字段
    List<Long> mentionedUserIds,
    String content,
    List<PostPublishedEvent.ReviewImage> imageThumbs
) {
    public CommentCreatedEvent(
        long commentId,
        long postId,
        String postTitle,
        long authorId,
        Long targetUserId,
        Long parentCommentId,
        String excerpt,
        String originalExcerpt,
        List<Long> mentionedUserIds
    ) {
        this(
            commentId,
            postId,
            postTitle,
            authorId,
            targetUserId,
            parentCommentId,
            excerpt,
            originalExcerpt,
            mentionedUserIds,
            null,
            List.of()
        );
    }

    public static final String EXCHANGE = "meng.content.topic";
    public static final String EVENT_TYPE = "comment.created";
}
