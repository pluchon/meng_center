package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.*;
import com.pluchon.meng.content.service.EmotionCatalog;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class EmotionCatalogTest {
    @Test void importsConfirmedCatalogIdempotentlyAndPreservesDisabledTokens() throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:emotion"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE emotion(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(12) UNIQUE,series VARCHAR(20),image_key VARCHAR(60),sort INT,enabled BOOLEAN,description VARCHAR(120),meaning VARCHAR(60),tags CLOB,catalog_version VARCHAR(80),delete_state INT DEFAULT 0)");
        var catalog=new EmotionCatalog();ReflectionTestUtils.setField(catalog,"jdbc",jdbc);
        catalog.run(null);catalog.run(null);
        assertEquals(24,catalog.read().items().size());
        assertEquals(19,catalog.read().items().stream().filter(e -> e.series().equals("cirno")).count());
        jdbc.update("UPDATE emotion SET enabled=0 WHERE name='好耶'");catalog.run(null);
        var stopped=catalog.read().items().stream().filter(e -> e.name().equals("好耶")).findFirst().orElseThrow();
        assertFalse(stopped.enabled());assertEquals("cirnoCheerEmotion",stopped.imageKey());
        jdbc.update("UPDATE emotion SET meaning='未更新版本的内容' WHERE name='好耶'");
        assertThrows(IllegalStateException.class,() -> catalog.run(null));
    }
}
