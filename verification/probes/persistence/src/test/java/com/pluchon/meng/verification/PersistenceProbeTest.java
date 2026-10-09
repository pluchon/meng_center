package com.pluchon.meng.verification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PersistenceProbeTest.Application.class)
class PersistenceProbeTest {
    @SpringBootApplication
    static class Application {}

    @TableName("verification_persistence_probe")
    public static class ProbeRecord {
        @TableId(type = IdType.AUTO) private Long id;
        private String marker;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getMarker() { return marker; }
        public void setMarker(String marker) { this.marker = marker; }
    }

    @Mapper
    public interface ProbeMapper extends BaseMapper<ProbeRecord> {}

    @Autowired ProbeMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayMigrationAndMybatisPlusReadWriteUseRealMysql() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM verification_flyway_history WHERE success = 1 AND version = '1'", Integer.class));
        var record = new ProbeRecord();
        record.setMarker("probe");
        assertEquals(1, mapper.insert(record));
        assertNotNull(record.getId());
        assertEquals("probe", mapper.selectById(record.getId()).getMarker());
        assertEquals(1, mapper.deleteById(record.getId()));
    }
}
