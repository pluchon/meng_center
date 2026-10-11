package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.validation.CodePointLength;
import com.pluchon.meng.content.validation.PostRules;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

// 草稿保存参数
public record SaveDraftDTO(
        // 帖子分类
        PostCategory category,
        // 标题
        @CodePointLength(max = PostRules.TITLE_MAX) String title,
        // 正文
        @CodePointLength(max = PostRules.CONTENT_MAX) String content,
        // 配图编号集合
        @Size(max = PostRules.IMAGES_MAX) List<@NotNull @Positive Long> imageIds,
        // 封面配图编号
        @Positive Long coverImageId,
        // 标签名称集合
        @Size(max = PostRules.TAGS_MAX) List<String> tags) {

    public SaveDraftDTO {
        title = title == null ? "" : title;
        content = content == null ? "" : content;
        imageIds = imageIds == null ? List.of() : imageIds;
        tags = tags == null ? List.of() : tags;
    }
}
