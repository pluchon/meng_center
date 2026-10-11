package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.validation.CodePointLength;

import jakarta.validation.constraints.*;

import java.util.List;

public record CreateCommentDTO(
        @CodePointLength(max = 500) String content,
        @Size(max = 10) List<@NotNull @Positive Long> imageIds,
        @Positive Long replyToCommentId) {
    public CreateCommentDTO {
        content = content == null ? "" : content;
        imageIds = imageIds == null ? List.of() : imageIds;
    }
}
