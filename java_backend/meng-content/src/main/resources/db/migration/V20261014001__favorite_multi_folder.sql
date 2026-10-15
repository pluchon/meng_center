ALTER TABLE post_favorite DROP INDEX uk_post_user,
 ADD UNIQUE KEY uk_post_user_folder(post_id,user_id,folder_id),
 ADD INDEX idx_post_user(post_id,user_id);
