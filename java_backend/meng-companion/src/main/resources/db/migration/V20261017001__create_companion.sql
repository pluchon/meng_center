CREATE TABLE companion_character (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(20) NOT NULL UNIQUE, name VARCHAR(30) NOT NULL, tagline VARCHAR(100) NOT NULL,
 avatar VARCHAR(300) NOT NULL DEFAULT '', portrait VARCHAR(300) NOT NULL DEFAULT '',
 stage_names JSON NOT NULL, sort INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE companion_bond (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 affection INT NOT NULL DEFAULT 0, stage INT NOT NULL DEFAULT 1, revision BIGINT NOT NULL DEFAULT 0,
 first_chat_time DATETIME(6), last_chat_time DATETIME(6), chat_days INT NOT NULL DEFAULT 0,
 today_gain INT NOT NULL DEFAULT 0, today_date DATE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_bond(user_id,character_code)
);
CREATE TABLE companion_session (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 title VARCHAR(80) NOT NULL DEFAULT '', summary TEXT NOT NULL, summary_until_message_id BIGINT NOT NULL DEFAULT 0,
 message_count INT NOT NULL DEFAULT 0, last_message_time DATETIME(6),
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_sessions(user_id,character_code,delete_state,last_message_time)
);
CREATE TABLE companion_message (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, role VARCHAR(20) NOT NULL,
 content TEXT NOT NULL, bubbles JSON, image_ids JSON, work_id BIGINT, affection_delta INT NOT NULL DEFAULT 0,
 safety_label VARCHAR(30), completed BOOLEAN NOT NULL DEFAULT FALSE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_messages(session_id,delete_state,id)
);
CREATE TABLE companion_memory (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 content VARCHAR(240) NOT NULL, content_hash CHAR(64) NOT NULL, source_message_id BIGINT NOT NULL,
 last_used_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_memories(user_id,character_code,delete_state,last_used_time),
 UNIQUE KEY uk_memory(user_id,character_code,content_hash)
);
CREATE TABLE companion_work (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 session_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL, title VARCHAR(80) NOT NULL, body MEDIUMTEXT,
 file_path VARCHAR(200),
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_works(user_id,delete_state,character_code,id)
);
CREATE TABLE companion_safety_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, character_code VARCHAR(20) NOT NULL,
 message_id BIGINT NOT NULL, kind VARCHAR(30) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_safety(user_id,create_time)
);
CREATE TABLE companion_image (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, file_path VARCHAR(200) NOT NULL UNIQUE,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0,
 KEY ix_images(user_id,delete_state)
);
INSERT INTO companion_character(code,name,tagline,stage_names,sort) VALUES
 ('TSUNDERE','白濑凛','嘴硬心软，关心藏不住',JSON_ARRAY('初识','熟悉','朋友','亲近','羁绊'),1),
 ('GENKI','日向小葵','想把每个小发现都讲给你听',JSON_ARRAY('初识','熟悉','朋友','亲近','羁绊'),2),
 ('NEKO','奶糖','随性撒娇，偶尔悄悄凑过来',JSON_ARRAY('初识','熟悉','朋友','亲近','羁绊'),3);
