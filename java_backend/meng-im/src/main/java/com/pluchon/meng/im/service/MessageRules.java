package com.pluchon.meng.im.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.im.controller.ImDTO;
import java.util.HashSet;
import java.util.regex.Pattern;

// 消息体边界和表情上限
public final class MessageRules {

    private static final Pattern EMOTION = Pattern.compile(
        "\\[表情:[^\\s\\[\\]]{1,12}\\]"
    );

    private MessageRules() {}

    public static void validate(ImDTO.Send dto) {
        String text = dto.content() == null ? "" : dto.content().strip();
        if (
            dto.peerId() < 1 ||
            dto.clientMsgId() == null ||
            dto.clientMsgId().isBlank() ||
            dto.clientMsgId().length() > 64 ||
            dto.imageIds() == null ||
            dto.imageIds().size() > 10 ||
            dto
                .imageIds()
                .stream()
                .anyMatch(id -> id == null || id < 1) ||
            new HashSet<>(dto.imageIds()).size() != dto.imageIds().size() ||
            (text.isEmpty() && dto.imageIds().isEmpty()) ||
            text.codePointCount(0, text.length()) > 500 ||
            EMOTION.matcher(text).results().count() > 10 ||
            EMOTION.matcher(text).replaceAll("").contains("[表情:")
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}
