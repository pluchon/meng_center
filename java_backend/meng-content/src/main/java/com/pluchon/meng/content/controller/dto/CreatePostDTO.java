package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.validation.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreatePostDTO(
    @NotNull PostCategory category,
    @NotBlank @CodePointLength(max = PostRules.TITLE_MAX) String title,
    @NotBlank @CodePointLength(max = PostRules.CONTENT_MAX) String content,
    @NotNull
    @Size(max = PostRules.IMAGES_MAX)
    List<@NotNull @Positive Long> imageIds,
    @Positive Long coverImageId,
    @Size(max = PostRules.TAGS_MAX) List<String> tags,
    @Positive Long draftId
) {
    public CreatePostDTO {
        tags = tags == null ? List.of() : tags;
    }
}
