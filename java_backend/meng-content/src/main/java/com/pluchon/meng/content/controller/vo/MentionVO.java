package com.pluchon.meng.content.controller.vo;

// 评论里被提到的用户，昵称是发出评论时的昵称
public record MentionVO(long userId, String nickname) {}
