CREATE TABLE favorite_folder (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 name VARCHAR(12) NULL, description VARCHAR(50) NULL,
 cover_name VARCHAR(50) NULL, default_owner_id BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 delete_state TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_default_owner(default_owner_id), INDEX idx_owner(user_id,delete_state)
);
INSERT INTO favorite_folder(user_id,default_owner_id) SELECT DISTINCT user_id,user_id FROM post_favorite;
ALTER TABLE post_favorite ADD COLUMN folder_id BIGINT NULL;
UPDATE post_favorite f JOIN favorite_folder d ON d.default_owner_id=f.user_id SET f.folder_id=d.id;
ALTER TABLE post_favorite MODIFY folder_id BIGINT NOT NULL, ADD INDEX idx_folder(folder_id,id);
