ALTER TABLE companion_bond
 ADD level INT NOT NULL DEFAULT 2, ADD level_changed BOOLEAN NOT NULL DEFAULT FALSE,
 ADD profile VARCHAR(1200) NOT NULL DEFAULT '', ADD profile_revision BIGINT NOT NULL DEFAULT 0,
 ADD time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Taipei';
ALTER TABLE companion_session
 ADD window_start_message_id BIGINT NOT NULL DEFAULT 0, ADD unread BOOLEAN NOT NULL DEFAULT FALSE,
 ADD episode_until_message_id BIGINT NOT NULL DEFAULT 0;
UPDATE companion_session SET window_start_message_id=summary_until_message_id;
ALTER TABLE companion_message
 ADD client_message_id VARCHAR(80), ADD turn_id BIGINT, ADD turn_state VARCHAR(20),
 ADD kind VARCHAR(20) NOT NULL DEFAULT 'TEXT', ADD sticker VARCHAR(60),
 ADD feeling VARCHAR(100), ADD intent VARCHAR(100), ADD prompt_version VARCHAR(180),
 ADD model VARCHAR(120), ADD memory_ids JSON, ADD metrics JSON,
 ADD request_work_id BIGINT, ADD request_revision BIGINT NOT NULL DEFAULT 0,
 ADD regenerates_message_id BIGINT,
 ADD time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Taipei',
 ADD UNIQUE KEY uk_message_client(session_id,client_message_id),
 ADD KEY ix_turn(turn_id), ADD KEY ix_pending(session_id,turn_state,id);
UPDATE companion_message SET turn_state=CASE WHEN completed=1 THEN 'COMMITTED' ELSE 'FAILED' END WHERE role='USER';
ALTER TABLE companion_memory
 ADD kind VARCHAR(20) NOT NULL DEFAULT 'LIFE', ADD keywords JSON, ADD importance INT NOT NULL DEFAULT 5,
 ADD embedding JSON, ADD pinned BOOLEAN NOT NULL DEFAULT FALSE,
 ADD valid_from DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD invalid_at DATETIME(6),
 ADD superseded_by BIGINT, ADD due_date DATE, ADD done BOOLEAN NOT NULL DEFAULT FALSE,
 ADD notified BOOLEAN NOT NULL DEFAULT FALSE, ADD source_session_id BIGINT,
 ADD mentioned_at DATETIME(6), ADD KEY ix_promises(kind,due_date,done,notified);
UPDATE companion_memory m JOIN companion_message msg ON msg.id=m.source_message_id
 SET m.source_session_id=msg.session_id;
ALTER TABLE companion_work ADD parent_id BIGINT;

CREATE TABLE companion_card (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, character_code VARCHAR(20) NOT NULL,
 version VARCHAR(80) NOT NULL, content JSON NOT NULL, active BOOLEAN NOT NULL DEFAULT FALSE,
 rollout_percent INT NOT NULL DEFAULT 100,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_card(character_code,version)
);
CREATE TABLE companion_episode (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 session_id BIGINT NOT NULL, start_time DATETIME(6), end_time DATETIME(6),
 summary TEXT NOT NULL, emotion VARCHAR(100), open_threads VARCHAR(1200), embedding JSON,
 heat DOUBLE NOT NULL DEFAULT 1, until_message_id BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_episode(session_id,until_message_id), KEY ix_episode_recall(user_id,character_code,delete_state,heat)
);
CREATE TABLE companion_line (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, character_code VARCHAR(20) NOT NULL,
 level INT NOT NULL, stage INT NOT NULL, scene VARCHAR(30) NOT NULL,
 dialogue JSON NOT NULL, version VARCHAR(80) NOT NULL, embedding JSON, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_line(character_code,level,stage,scene)
);
CREATE TABLE companion_daily (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, character_code VARCHAR(20) NOT NULL,
 date DATE NOT NULL, content VARCHAR(1200) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_daily(character_code,date)
);
CREATE TABLE companion_drawing (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 message_id BIGINT NOT NULL, instruction VARCHAR(2000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 work_id BIGINT, reservation JSON NOT NULL, revision BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_drawing(message_id), KEY ix_drawing_session(session_id,status)
);
CREATE TABLE companion_feedback (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, message_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL, rejected_content TEXT NOT NULL, replacement_message_id BIGINT,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_feedback(user_id,message_id,kind)
);
CREATE TABLE companion_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL,
 event_type VARCHAR(100) NOT NULL, payload JSON NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 retry_count INT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_outbox(event_id), KEY ix_outbox(status,id)
);
CREATE TABLE companion_processed (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL UNIQUE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0
);
