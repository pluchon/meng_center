package com.pluchon.meng.content.controller.vo;

// 输入 @ 时可以选的人：本帖作者和评论过本帖的人
public record MentionCandidateVO(long id, String nickname, String avatar, int level, boolean postAuthor) {}
