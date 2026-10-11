package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.content.validation.CommentRules;

import org.junit.jupiter.api.Test;

import java.util.List;

class CommentRulesTest {

    @Test
    void sharesAttachmentLimitBetweenEmotionsAndImages() {
        assertDoesNotThrow(() -> CommentRules.validate("[表情:水滴]".repeat(9), List.of(1L)));
        assertThrows(
                BusinessException.class,
                () -> CommentRules.validate("[表情:水滴]".repeat(10), List.of(1L)));
        assertThrows(
                BusinessException.class,
                () -> CommentRules.validate("[表情:水滴]".repeat(6), List.of(1L, 2L, 3L, 4L, 5L)));
    }

    @Test
    void acceptsUnicodeAndImageOnly() {
        assertDoesNotThrow(() -> CommentRules.validate("😀".repeat(500), List.of()));
        assertDoesNotThrow(() -> CommentRules.validate("", List.of(1L)));
        assertThrows(
                BusinessException.class, () -> CommentRules.validate("😀".repeat(501), List.of()));
    }

    @Test
    void rejectsEmptyAndDuplicateImages() {
        assertThrows(BusinessException.class, () -> CommentRules.validate(" ", List.of()));
        assertThrows(BusinessException.class, () -> CommentRules.validate("图", List.of(1L, 1L)));
    }

    @Test
    void validatesEmotionTokensWithoutExecutingMarkup() {
        assertDoesNotThrow(() -> CommentRules.validate("[表情:未知名字]".repeat(10), List.of()));
        assertThrows(
                BusinessException.class,
                () -> CommentRules.validate("[表情:水滴]".repeat(11), List.of()));
        assertThrows(BusinessException.class, () -> CommentRules.validate("[表情:有 空格]", List.of()));
        assertThrows(BusinessException.class, () -> CommentRules.validate("[表情:水滴", List.of()));
        assertDoesNotThrow(() -> CommentRules.validate("<script>alert(1)</script>", List.of()));
    }
}
