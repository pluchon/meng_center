package com.pluchon.meng.im;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.im.controller.ImDTO;
import com.pluchon.meng.im.service.MessageRules;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

// Unicode 和附件边界属于发送协议不变量
class MessageRulesTest {

    private static ImDTO.Send send(String content, List<Long> images) {
        return new ImDTO.Send(2, "client-1", content, images);
    }

    @Test
    void unicodeCountsCharactersRatherThanUtf16() {
        assertDoesNotThrow(() ->
            MessageRules.validate(send("😀".repeat(500), List.of()))
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send("😀".repeat(501), List.of()))
        );
    }

    @Test
    void requiresTextOrImagesAndAllowsBoth() {
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send(" ", List.of()))
        );
        assertDoesNotThrow(() ->
            MessageRules.validate(send("text", List.of(1L)))
        );
        assertDoesNotThrow(() ->
            MessageRules.validate(send(null, List.of(1L)))
        );
    }

    @Test
    void imageIdsMustBeDistinctAndBounded() {
        assertDoesNotThrow(() ->
            MessageRules.validate(
                send(null, LongStream.rangeClosed(1, 10).boxed().toList())
            )
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(
                send(null, LongStream.rangeClosed(1, 11).boxed().toList())
            )
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send(null, List.of(1L, 1L)))
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send(null, List.of(0L)))
        );
    }

    @Test
    void emotionLimitIsIndependentOfTextLength() {
        assertDoesNotThrow(() ->
            MessageRules.validate(send("[表情:萌萌]".repeat(10), List.of()))
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send("[表情:萌萌]".repeat(11), List.of()))
        );
        assertThrows(BusinessException.class, () ->
            MessageRules.validate(send("[表情:", List.of()))
        );
    }
}
