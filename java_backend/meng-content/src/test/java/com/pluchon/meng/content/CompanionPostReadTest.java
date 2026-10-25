package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.content.mapper.CompanionPostMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class CompanionPostReadTest {

    @Test
    void onlyPublicPostsExposeBoundedContentAndPublicationTime()
        throws Exception {
        var source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:post_read_" +
                UUID.randomUUID() +
                ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
            "CREATE TABLE post(id BIGINT PRIMARY KEY,title VARCHAR(80),content CLOB,author_id BIGINT,review_state VARCHAR(20),delete_state INT,create_time TIMESTAMP)"
        );
        jdbc.execute(
            "CREATE TABLE public_author_snapshot(user_id BIGINT,nickname VARCHAR(60),delete_state INT)"
        );
        var published = LocalDateTime.of(2026, 10, 25, 0, 0);
        jdbc.update(
            "INSERT INTO post VALUES(1,'公开帖子',?,41,'NORMAL',0,?)",
            "正文".repeat(2000),
            published
        );
        jdbc.update(
            "INSERT INTO post VALUES(2,'审核中','内容',41,'IN_REVIEW',0,?)",
            published
        );
        jdbc.update(
            "INSERT INTO post VALUES(3,'已删除','内容',41,'NORMAL',1,?)",
            published
        );
        jdbc.update(
            "INSERT INTO public_author_snapshot VALUES(41,'公开昵称',0)"
        );
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CompanionPostMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        try (
            var session = new SqlSessionTemplate(factory.getObject())
                .getSqlSessionFactory()
                .openSession()
        ) {
            var mapper = session.getMapper(CompanionPostMapper.class);
            var result = mapper.read(1);
            assertEquals(3000, result.content().length());
            assertEquals("公开昵称", result.authorNickname());
            assertEquals(published, result.createTime());
            assertNull(mapper.read(2));
            assertNull(mapper.read(3));
            assertNull(mapper.read(999));
        }
    }
}
