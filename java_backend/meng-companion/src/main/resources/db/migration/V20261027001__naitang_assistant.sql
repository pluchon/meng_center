-- 旧角色会话与消息按逻辑删除处理，历史迁移保持原样。
UPDATE companion_message m JOIN companion_session s ON s.id=m.session_id
 SET m.delete_state=1 WHERE s.character_code IN ('TSUNDERE','GENKI');
UPDATE companion_session SET delete_state=1 WHERE character_code IN ('TSUNDERE','GENKI');
UPDATE companion_memory SET delete_state=1,embedding=NULL,invalid_at=UTC_TIMESTAMP(6)
 WHERE character_code IN ('TSUNDERE','GENKI') OR kind IN ('PROMISE','BIT');
UPDATE companion_bond SET delete_state=1 WHERE character_code IN ('TSUNDERE','GENKI');
UPDATE companion_card SET active=FALSE,delete_state=1 WHERE character_code<>'NEKO';
DELETE FROM companion_line;
DELETE FROM companion_outbox WHERE event_type<>'turn.committed';
UPDATE companion_message SET delete_state=1 WHERE kind NOT IN ('TEXT','STICKER') OR work_id IS NOT NULL;
DROP TABLE companion_character,companion_episode,companion_arc,companion_daily,
 companion_work,companion_drawing,companion_feedback,companion_web_image;
ALTER TABLE companion_bond DROP affection,DROP stage,DROP level,DROP level_changed,
 DROP first_chat_time,DROP last_chat_time,DROP chat_days,DROP today_gain,DROP today_date,DROP user_style;
ALTER TABLE companion_session DROP episode_until_message_id,DROP context_tier,DROP threads;
ALTER TABLE companion_message DROP work_id,DROP affection_delta,DROP feeling,DROP intent,
 DROP request_work_id,DROP regenerates_message_id,DROP move,DROP inner_thought,DROP lint,
 DROP feeling_level,DROP cards,DROP retracted,DROP reaction;
ALTER TABLE companion_memory DROP INDEX ix_promises,DROP due_date,DROP done,DROP notified,DROP mentioned_at;
ALTER TABLE companion_line DROP INDEX ix_line,DROP level,DROP stage,DROP embedding,
 ADD KEY ix_line(character_code,version,scene);
ALTER TABLE companion_card DROP rollout_percent;
ALTER TABLE companion_image DROP source,DROP source_url,DROP page_url;
UPDATE companion_session SET context_snapshot=NULL,snapshot_time=NULL WHERE character_code='NEKO';
