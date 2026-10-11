CREATE TABLE author_daily_stat (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 author_id BIGINT NOT NULL,
 stat_date DATE NOT NULL,
 view_count BIGINT NOT NULL DEFAULT 0,
 like_count BIGINT NOT NULL DEFAULT 0,
 comment_count BIGINT NOT NULL DEFAULT 0,
 favorite_count BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_author_date(author_id, stat_date)
) CHARACTER SET utf8mb4;
ALTER TABLE post ADD INDEX idx_author_created(author_id, delete_state, create_time, id);
ALTER TABLE post ADD INDEX idx_author_hot(author_id, delete_state, hot_score, id);
