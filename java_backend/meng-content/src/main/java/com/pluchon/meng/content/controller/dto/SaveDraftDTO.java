package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.validation.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveDraftDTO(
    PostCategory category,
    @CodePointLength(max = PostRules.TITLE_MAX) String title,
    @CodePointLength(max = PostRules.CONTENT_MAX) String content,
    @Size(max = PostRules.IMAGES_MAX) List<@NotNull @Positive Long> imageIds,
    @Positive Long coverImageId,
    @Size(max = PostRules.TAGS_MAX) List<String> tags
) {
    public SaveDraftDTO {
        title = title == null ? "" : title;
        content = content == null ? "" : content;
        imageIds = imageIds == null ? List.of() : imageIds;
        tags = tags == null ? List.of() : tags;
    }
}
