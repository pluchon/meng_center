ALTER TABLE companion_message ADD cards JSON;
CREATE TABLE companion_web_image (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, message_id BIGINT NOT NULL,
 file_path VARCHAR(80) NOT NULL, width INT NOT NULL, height INT NOT NULL,
 domain VARCHAR(255) NOT NULL, page_url VARCHAR(1000) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 delete_state TINYINT NOT NULL DEFAULT 0, KEY ix_web_image_message(message_id), KEY ix_web_image_owner(id,user_id)
);
