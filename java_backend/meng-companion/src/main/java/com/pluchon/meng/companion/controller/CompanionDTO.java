package com.pluchon.meng.companion.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

// 请求只携带内容和本人图片编号，不允许传入用户身份、阶段或好感
public final class CompanionDTO {

    private CompanionDTO() {}

    public record Send(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{1,80}")
        String clientMessageId,
        @Size(max = 1000) String content,
        @Size(max = 3) List<@Positive Long> imageIds,
        @Positive Long workId
    ) {
        public Send {
            imageIds = imageIds == null ? List.of() : List.copyOf(imageIds);
        }
    }

    public record Level(@Min(1) @Max(3) int level) {}

    public record Memory(
        @Size(min = 1, max = 60) String content,
        Boolean pinned
    ) {}
}
