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
'@ | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
    foreach ($name in @('V20261017002__companion_runtime.sql','V20261017003__seed_companion_cards.sql')) {
        Get-Content -LiteralPath "$migrationRoot/$name" -Raw | & docker exec -i -e MYSQL_PWD $container mysql -uroot $database
        if ($LASTEXITCODE -ne 0) { throw "Companion migration failed: $name" }
    }
    @'
DELIMITER $$
CREATE PROCEDURE verify_runtime()
BEGIN
 IF (SELECT COUNT(*) FROM companion_card WHERE JSON_LENGTH(content,'$.levels')=3 AND active=1)<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing character cards'; END IF;
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
 SELECT 'Companion migration, source backfill, cards, idempotency and outbox checks passed.';
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
