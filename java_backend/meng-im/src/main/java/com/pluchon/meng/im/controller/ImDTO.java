package com.pluchon.meng.im.controller;

import com.pluchon.meng.im.entity.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

// 私信与已读请求
public final class ImDTO {

    private ImDTO() {}

    public record Send(
        @Positive long peerId,
        @NotBlank @Size(max = 64) String clientMsgId,
        String content,
        @NotNull @Size(max = 10) List<@Positive Long> imageIds
    ) {}

    public record Read(@Positive long messageId) {}

    public record NoticeRead(
        @NotNull Channel channel,
        String source,
        String category
    ) {}

    public record Block(@Positive long userId) {}

    public record Setting(boolean hidden) {}
}
