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
    List<Long> mentionedUserIds
) {
    public static final String EXCHANGE = "meng.content.topic";
    public static final String EVENT_TYPE = "comment.created";
}
