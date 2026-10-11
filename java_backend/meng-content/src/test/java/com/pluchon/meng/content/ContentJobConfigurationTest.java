package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.content.config.ContentJobConfiguration;

import org.junit.jupiter.api.Test;

class ContentJobConfigurationTest {

    @Test
    void rejectsMissingBlankAndOfficialDefaultToken() {
        for (String token : new String[] {null, "", "  ", "default_token", " default_token "}) {
            var error =
                    assertThrows(
                            IllegalStateException.class,
                            () -> ContentJobConfiguration.requireToken(token));
            assertTrue(error.getMessage().contains("MENG_XXL_JOB_ACCESS_TOKEN"));
        }
    }

    @Test
    void preservesConfiguredToken() {
        assertEquals(
                "unique-environment-token",
                ContentJobConfiguration.requireToken("unique-environment-token"));
    }
}
