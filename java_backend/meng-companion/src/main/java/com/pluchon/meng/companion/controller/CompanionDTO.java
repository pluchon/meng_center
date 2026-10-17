package com.pluchon.meng.companion.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

// 请求只携带内容和本人图片编号，不允许传入用户身份、阶段或好感
public final class CompanionDTO {
    private CompanionDTO() {}
    public record Send(@NotBlank @Size(max=1000) String content, @Size(max=3) List<@Positive Long> imageIds) {
        public Send { imageIds = imageIds == null ? List.of() : List.copyOf(imageIds); }
    }
}
