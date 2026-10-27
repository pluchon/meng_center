-- 同一事实可以在失效或清空后重新记住，历史记录保留原编号和取代关系。
ALTER TABLE companion_memory
 DROP INDEX uk_memory,
 ADD active_content_hash CHAR(64) GENERATED ALWAYS AS (
   CASE WHEN delete_state=0 AND invalid_at IS NULL THEN content_hash ELSE NULL END
 ) STORED,
 ADD UNIQUE KEY uk_memory_active(user_id,character_code,active_content_hash);
