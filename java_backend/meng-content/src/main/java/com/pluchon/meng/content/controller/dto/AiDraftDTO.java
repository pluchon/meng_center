package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.entity.enums.PostCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 发帖辅助的草稿输入
public record AiDraftDTO(
    @NotNull PostCategory category,
    @NotNull @Size(max = 50) String title,
    @NotNull @Size(max = 10000) String content,
    @Size(max = 5) List<@Size(max = 12) String> selectedTags
) {}
