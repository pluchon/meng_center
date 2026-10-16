package com.pluchon.meng.content.controller.dto;

import com.pluchon.meng.content.validation.PostRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

// 带内容版本和逐图结论的帖子分析结果
public record PostAnalysisDTO(
    @Positive long postId,
    @NotNull LocalDateTime version,
    @NotBlank @Size(max = 255) String modelVersion,
    @NotNull @Size(min = 1, max = 5) List<@NotBlank @Size(max = 100) String> topics,
    @NotNull @Size(max = 8) List<@Size(max = 100) String> keywords,
    @Min(1) @Max(5) int quality,
    @Pattern(regexp = "PASS|SUSPECT|BLOCK") String verdict,
    @NotNull @Size(max = 200) List<@Size(max = 150) String> labels,
    @NotNull @Size(max = 200) String reason,
    @NotNull @Size(min = 1024, max = 1024) List<Float> embedding,
    @Pattern(regexp = "SUCCESS|FAILED") String analyzeState,
    boolean imagesChanged,
    @NotNull
    @Size(max = PostRules.IMAGES_MAX + 1)
    List<@jakarta.validation.Valid ImageReview> imageReviews
) {
    public record ImageReview(
        @Positive long imageId,
        @NotNull @Pattern(regexp = "PASS|SUSPECT|BLOCK") String verdict,
        @NotNull List<String> labels,
        @NotNull @Size(max = 200) String reason
    ) {}
}
