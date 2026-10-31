package com.pluchon.meng.common.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class StrictTextFieldsTest {

    record Body(String title, Long id) {}

    private final JsonMapper mapper = mapper();

    private static JsonMapper mapper() {
        var builder = JsonMapper.builder();
        new CommonWebAutoConfiguration().strictTextFields().customize(builder);
        return builder.build();
    }

    @Test
    void scalarValuesAreNotCoercedIntoTextFields() {
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"title\":123}", Body.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"title\":1.5}", Body.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"title\":true}", Body.class));
    }

    @Test
    void validBodiesStillParse() throws Exception {
        assertEquals(new Body("标题", 7L), mapper.readValue("{\"title\":\"标题\",\"id\":7}", Body.class));
        assertEquals(new Body(null, null), mapper.readValue("{\"title\":null}", Body.class));
    }
}
