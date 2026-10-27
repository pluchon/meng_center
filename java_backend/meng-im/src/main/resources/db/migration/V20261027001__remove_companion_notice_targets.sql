-- 旧陪伴通知停止展示，移除其跳转字段。
UPDATE notification SET delete_state=1 WHERE category='COMPANION';
ALTER TABLE notification DROP companion_code,DROP companion_session_id;
