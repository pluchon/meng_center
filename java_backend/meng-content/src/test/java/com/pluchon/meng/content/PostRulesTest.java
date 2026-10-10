package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.content.validation.*;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostRulesTest {

    record Text(@CodePointLength(max = 2) String value) {}

    @Test
    void countsUnicodeCodePoints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(
                factory.getValidator().validate(new Text("😀😀")).isEmpty()
            );
            assertFalse(
                factory.getValidator().validate(new Text("😀😀😀")).isEmpty()
            );
        }
    }

    @Test
    void normalizesAndDeduplicatesTags() {
        assertEquals(
            List.of("ＡＢＣ"),
            PostRules.tags(List.of(" ＡＢＣ ", "abc"))
        );
        assertThrows(RuntimeException.class, () ->
            PostRules.tags(List.of("bad tag"))
        );
        assertThrows(RuntimeException.class, () ->
            PostRules.tags(List.of("＃tag"))
        );
        assertThrows(RuntimeException.class, () ->
            PostRules.tags(List.of("萌".repeat(13)))
        );
    }

    @Test
    void separatesCoverFromAttachments() {
        assertThrows(RuntimeException.class, () ->
            PostRules.images(List.of(1L), 1L)
        );
        assertThrows(RuntimeException.class, () ->
            PostRules.images(List.of(1L, 1L), null)
        );
        assertDoesNotThrow(() ->
            PostRules.images(
                java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(),
                11L
            )
        );
    }
}
