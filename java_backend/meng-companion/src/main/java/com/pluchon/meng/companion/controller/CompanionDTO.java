package com.pluchon.meng.companion.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

// 身份由网关注入，客户端只提交消息内容与本人图片编号。
public final class CompanionDTO {
    private CompanionDTO() {}
    public record Send(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String clientMessageId,
        @Size(max=1000) String content,
        @Size(max=3) List<@Positive Long> imageIds
    ) {
        public Send { imageIds = imageIds == null ? List.of() : List.copyOf(imageIds); }
    }
}
