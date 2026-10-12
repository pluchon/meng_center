package com.pluchon.meng.content.controller.vo;

// 通知跳转所需的一级评论页和回复页位置
public record CommentLocationVO(
    long postId,
    long rootId,
    long page,
    long replyPage
) {}
