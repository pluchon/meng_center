ALTER TABLE companion_session
 ADD context_snapshot JSON, ADD snapshot_time DATETIME(6), ADD threads JSON;
ALTER TABLE companion_message
 ADD sources JSON, ADD retracted BOOLEAN NOT NULL DEFAULT FALSE, ADD reaction VARCHAR(12);
ALTER TABLE companion_memory ADD embedding_version VARCHAR(30);
ALTER TABLE companion_episode ADD embedding_version VARCHAR(30);
ALTER TABLE companion_daily ADD status VARCHAR(60) NOT NULL DEFAULT '';
ALTER TABLE companion_image
 ADD source VARCHAR(10) NOT NULL DEFAULT 'UPLOAD', ADD source_url VARCHAR(2000), ADD page_url VARCHAR(2000);
CREATE TABLE companion_arc (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 character_code VARCHAR(20) NOT NULL, week_start DATE NOT NULL, outline JSON NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_arc(character_code,week_start)
);
