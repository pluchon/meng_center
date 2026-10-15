package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.validation.CodePointLength;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

// 评论提交参数
public record CreateCommentDTO(
        // 正文
        @CodePointLength(max = 500) String content,
        // 配图编号集合
        @Size(max = 10) List<@NotNull @Positive Long> imageIds,
        // 回复目标评论编号
        @Positive Long replyToCommentId,
        // @ 到的用户编号集合
        @Size(max = 5) List<@NotNull @Positive Long> mentionUserIds) {

    public CreateCommentDTO {
        content = content == null ? "" : content;
        imageIds = imageIds == null ? List.of() : imageIds;
        mentionUserIds = mentionUserIds == null ? List.of() : mentionUserIds;
    }
}
