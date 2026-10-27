$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
$composeArguments = Get-MengComposeArguments
$container = & docker @composeArguments ps --quiet mysql
if (-not $container) { throw 'Start the project MySQL container first.' }
$database = 'meng_verify_companion_' + [Guid]::NewGuid().ToString('N')
if ($database -notmatch '^meng_verify_companion_[a-f0-9]{32}$') { throw 'Unsafe verification database name.' }
$priorPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:MYSQL_ROOT_PASSWORD
    "CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | & docker exec -i -e MYSQL_PWD $container mysql -uroot
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated companion verification database.' }
    $migrationRoot = "$PSScriptRoot/../java_backend/meng-companion/src/main/resources/db/migration"
    Get-Content -LiteralPath "$migrationRoot/V20261017001__create_companion.sql" -Raw | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
    if ($LASTEXITCODE -ne 0) { throw 'Original companion schema failed.' }
    @'
INSERT INTO companion_bond(user_id,character_code) VALUES(41,'NEKO');
INSERT INTO companion_session(user_id,character_code,summary,summary_until_message_id) VALUES(41,'NEKO','旧摘要',1);
INSERT INTO companion_message(session_id,role,content,completed) VALUES(1,'USER','喜欢蓝色',1);
INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id) VALUES(41,'NEKO','喜欢蓝色',SHA2('喜欢蓝色',256),1);
INSERT INTO companion_session(user_id,character_code,summary) VALUES(42,'TSUNDERE',''),(43,'GENKI','');
INSERT INTO companion_message(session_id,role,content,completed) VALUES(2,'USER','旧对话',1),(3,'USER','旧对话',1);
INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id) VALUES(42,'TSUNDERE','旧事实',SHA2('旧事实',256),2);

'@ | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
    foreach ($name in @('V20261017002__companion_runtime.sql','V20261017003__seed_companion_cards.sql','V20261023001__companion_context_tier.sql','V20261023002__companion_style.sql','V20261023003__companion_cards.sql','V20261025001__companion_runtime_v2.sql','V20261027001__naitang_assistant.sql','V20261027002__version_companion_memory_facts.sql')) {
        Get-Content -LiteralPath "$migrationRoot/$name" -Raw | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
        if ($LASTEXITCODE -ne 0) { throw "Companion migration failed: $name" }
    }
    @'
DELIMITER $$
CREATE PROCEDURE verify_runtime()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('companion_character','companion_work','companion_drawing','companion_feedback','companion_episode','companion_arc','companion_daily','companion_web_image')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Obsolete tables remain'; END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('companion_bond','companion_line') AND column_name IN ('affection','stage','level')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Obsolete columns remain'; END IF;
 IF (SELECT COUNT(*) FROM companion_session WHERE character_code<>'NEKO' AND delete_state=0)<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old conversations visible'; END IF;
 IF (SELECT COUNT(*) FROM companion_message WHERE session_id IN (2,3) AND delete_state=0)<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old messages visible'; END IF;
 IF (SELECT COUNT(*) FROM companion_memory WHERE character_code<>'NEKO' AND delete_state=0)<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old memories visible'; END IF;
 IF (SELECT window_start_message_id FROM companion_session WHERE id=1)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='History window migration failed'; END IF;
 IF (SELECT source_session_id FROM companion_memory WHERE id=1)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Memory source migration failed'; END IF;
 INSERT INTO companion_message(session_id,role,content,client_message_id,turn_state) VALUES(1,'USER','第一条','same-client','RECEIVED');
 INSERT IGNORE INTO companion_message(session_id,role,content,client_message_id,turn_state) VALUES(1,'USER','重复提交','same-client','RECEIVED');
 IF (SELECT COUNT(*) FROM companion_message WHERE client_message_id='same-client')<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Idempotency key is not unique'; END IF;
 UPDATE companion_memory SET pinned=1,invalid_at=UTC_TIMESTAMP(6),embedding=JSON_ARRAY(1,0) WHERE id=1;
 IF (SELECT COUNT(*) FROM companion_memory WHERE user_id=41 AND invalid_at IS NULL AND delete_state=0)<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalidated memory still visible'; END IF;
 INSERT INTO companion_outbox(event_id,event_type,payload) VALUES('same-event','turn.committed',JSON_OBJECT('userId',41));
 INSERT IGNORE INTO companion_outbox(event_id,event_type,payload) VALUES('same-event','turn.committed',JSON_OBJECT('userId',41));
 IF (SELECT COUNT(*) FROM companion_outbox)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Outbox event is not unique'; END IF;
 UPDATE companion_session SET context_snapshot=JSON_OBJECT('profile','原文'),snapshot_time=UTC_TIMESTAMP(6) WHERE id=1;
 UPDATE companion_message SET sources=JSON_ARRAY(JSON_OBJECT('postId',12,'title','站内来源')) WHERE id=1;
 IF (SELECT JSON_LENGTH(sources) FROM companion_message WHERE id=1)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Site sources failed'; END IF;
 INSERT INTO companion_image(user_id,file_path) VALUES(41,'sent.png'),(41,'pending.png'),(41,'expired.png'),(42,'other.png');
 UPDATE companion_image SET create_time=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY) WHERE file_path='expired.png';
 UPDATE companion_message SET image_ids=JSON_ARRAY(1,4) WHERE id=1;
 IF (SELECT COUNT(*) FROM companion_image i WHERE i.user_id=41 AND i.delete_state=0 AND NOT EXISTS(SELECT 1 FROM companion_message m JOIN companion_session s ON s.id=m.session_id WHERE s.user_id=i.user_id AND JSON_CONTAINS(m.image_ids,JSON_ARRAY(i.id))))<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pending images count failed'; END IF;
 IF (SELECT COUNT(*) FROM companion_image i WHERE i.user_id=42 AND i.delete_state=0 AND NOT EXISTS(SELECT 1 FROM companion_message m JOIN companion_session s ON s.id=m.session_id WHERE s.user_id=i.user_id AND JSON_CONTAINS(m.image_ids,JSON_ARRAY(i.id))))<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Image ownership isolation failed'; END IF;
 IF (SELECT COUNT(*) FROM companion_image i WHERE i.user_id=41 AND i.delete_state=0 AND i.create_time<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY) AND NOT EXISTS(SELECT 1 FROM companion_message m JOIN companion_session s ON s.id=m.session_id WHERE s.user_id=i.user_id AND JSON_CONTAINS(m.image_ids,JSON_ARRAY(i.id))))<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expired pending images selection failed'; END IF;
 INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id) VALUES(41,'NEKO','喜欢蓝色',SHA2('喜欢蓝色',256),1);
 SET @fresh_memory=LAST_INSERT_ID();
 INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id) VALUES(41,'NEKO','喜欢蓝色',SHA2('喜欢蓝色',256),1) ON DUPLICATE KEY UPDATE last_used_time=CURRENT_TIMESTAMP(6);
 IF (SELECT id FROM companion_memory WHERE user_id=41 AND character_code='NEKO' AND active_content_hash=SHA2('喜欢蓝色',256))<>@fresh_memory THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Active fact duplicate did not return its identifier'; END IF;
 UPDATE companion_memory SET delete_state=1,invalid_at=UTC_TIMESTAMP(6) WHERE user_id=41;
 INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id) VALUES(41,'NEKO','喜欢蓝色',SHA2('喜欢蓝色',256),1);
 IF LAST_INSERT_ID()=@fresh_memory OR (SELECT COUNT(*) FROM companion_memory WHERE user_id=41 AND content_hash=SHA2('喜欢蓝色',256))<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleared or invalidated fact could not be remembered again'; END IF;
 SELECT 'Naitang migration, legacy isolation, idempotency, outbox and private image limits passed.';
END$$
DELIMITER ;
CALL verify_runtime();
'@ | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
    if ($LASTEXITCODE -ne 0) { throw 'Companion database assertions failed.' }
} finally {
    if ($database -match '^meng_verify_companion_[a-f0-9]{32}$') {
        "DROP DATABASE IF EXISTS ${database};" | & docker exec -i -e MYSQL_PWD $container mysql -uroot
        if ($LASTEXITCODE -ne 0) { Write-Warning "Verification database was not removed: $database" }
    }
    $env:MYSQL_PWD = $priorPassword
}
