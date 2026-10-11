package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.content.validation.CodePointLength;
import com.pluchon.meng.content.validation.PostRules;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import java.util.List;

class PostRulesTest {

    record Text(@CodePointLength(max = 2) String value) {}

    @Test
    void countsUnicodeCodePoints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new Text("😀😀")).isEmpty());
            assertFalse(factory.getValidator().validate(new Text("😀😀😀")).isEmpty());
        }
    }

    @Test
    void normalizesAndDeduplicatesTags() {
        assertEquals(List.of("ＡＢＣ"), PostRules.tags(List.of(" ＡＢＣ ", "abc")));
        assertThrows(RuntimeException.class, () -> PostRules.tags(List.of("bad tag")));
        assertThrows(RuntimeException.class, () -> PostRules.tags(List.of("＃tag")));
        assertThrows(RuntimeException.class, () -> PostRules.tags(List.of("萌".repeat(13))));
    }

    @Test
    void separatesCoverFromAttachments() {
        assertThrows(RuntimeException.class, () -> PostRules.images(List.of(1L), 1L));
        assertThrows(RuntimeException.class, () -> PostRules.images(List.of(1L, 1L), null));
        assertDoesNotThrow(
                () ->
                        PostRules.images(
                                java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(),
                                11L));
    }
}
