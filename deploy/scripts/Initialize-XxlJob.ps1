$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
& "$PSScriptRoot/Initialize-LocalEnvironment.ps1"
Import-MengEnvironment
if ([string]::IsNullOrWhiteSpace($env:MENG_XXL_JOB_ACCESS_TOKEN) -or $env:MENG_XXL_JOB_ACCESS_TOKEN.Trim() -eq 'default_token') {
    throw 'MENG_XXL_JOB_ACCESS_TOKEN must be nonblank and different from default_token.'
}
$arguments=Get-MengComposeArguments
$container=& docker @arguments ps --quiet mysql
if(-not $container) { throw 'Start the project MySQL container first.' }
$priorPassword=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD
    $passwordHex=[Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($env:MENG_MYSQL_XXLJOB_PASSWORD))
    $sql=@"
CREATE DATABASE IF NOT EXISTS xxl_job CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @password=CONVERT(0x${passwordHex} USING utf8mb4);
SET @command=CONCAT('CREATE USER IF NOT EXISTS ''meng_xxl_job_app''@''%'' IDENTIFIED BY ',QUOTE(@password));
PREPARE command FROM @command;
EXECUTE command;
DEALLOCATE PREPARE command;
GRANT ALL PRIVILEGES ON xxl_job.* TO 'meng_xxl_job_app'@'%';
"@
    $sql | & docker exec -i -e MYSQL_PWD $container mysql -uroot
    if($LASTEXITCODE -ne 0) {throw 'XXL database initialization failed.'}
    Get-Content -LiteralPath "$PSScriptRoot/../xxl-job/schema.sql" -Raw | & docker exec -i -e MYSQL_PWD $container mysql -uroot
    if($LASTEXITCODE -ne 0) {throw 'XXL official schema initialization failed.'}
    $adminHex=[Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($env:MENG_XXL_JOB_ADMIN_PASSWORD))
    $tokenHex=[Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($env:MENG_XXL_JOB_ACCESS_TOKEN))
    $sql=@"
USE xxl_job;
INSERT INTO xxl_job_user(username,password,role) VALUES('admin',SHA2(CONVERT(0x${adminHex} USING utf8mb4),256),1)
ON DUPLICATE KEY UPDATE password=IF(password='8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92',VALUES(password),password);
INSERT INTO xxl_job_group(app_name,name,address_type,access_token,update_time)
VALUES('meng-content','内容服务',0,CONVERT(0x${tokenHex} USING utf8mb4),NOW())
ON DUPLICATE KEY UPDATE access_token=VALUES(access_token),update_time=NOW();
SET @group=(SELECT id FROM xxl_job_group WHERE app_name='meng-content');
"@
    foreach($job in @(@{handler='contentHotRebuild';cron='0 0 4 * * ?';timeout=600},@{handler='contentSearchRebuild';cron='0 10 4 * * ?';timeout=1800},@{handler='contentAiOutboxDispatch';cron='0 * * * * ?';timeout=55},@{handler='contentRecProfileRebuild';cron='0 20 4 * * ?';timeout=1800},@{handler='contentRecLogClean';cron='0 30 4 * * ?';timeout=600},@{handler='contentRecRankerTrain';cron='0 0 5 ? * MON';timeout=180})) {
        $handler=$job.handler;$cron=$job.cron;$timeout=$job.timeout
        $sql+=@"
INSERT INTO xxl_job_info(job_group,name,author,schedule_type,schedule_conf,misfire_strategy,executor_route_strategy,executor_handler,executor_block_strategy,executor_timeout,executor_fail_retry_count,glue_type,trigger_status,add_time,update_time,glue_updatetime)
SELECT @group,'${handler}','meng_center','CRON','${cron}','DO_NOTHING','FIRST','${handler}','DISCARD_LATER',${timeout},1,'BEAN',1,NOW(),NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM xxl_job_info WHERE job_group=@group AND executor_handler='${handler}');
UPDATE xxl_job_info SET schedule_type='CRON',schedule_conf='${cron}',misfire_strategy='DO_NOTHING',executor_route_strategy='FIRST',executor_block_strategy='DISCARD_LATER',executor_timeout=${timeout},executor_fail_retry_count=1,trigger_status=1,update_time=NOW()
WHERE job_group=@group AND executor_handler='${handler}';
"@
    }
    $sql+=@"
INSERT INTO xxl_job_group(app_name,name,address_type,access_token,update_time)
VALUES('meng-user','用户服务',0,CONVERT(0x${tokenHex} USING utf8mb4),NOW())
ON DUPLICATE KEY UPDATE access_token=VALUES(access_token),update_time=NOW();
SET @userGroup=(SELECT id FROM xxl_job_group WHERE app_name='meng-user');
INSERT INTO xxl_job_info(job_group,name,author,schedule_type,schedule_conf,misfire_strategy,executor_route_strategy,executor_handler,executor_block_strategy,executor_timeout,executor_fail_retry_count,glue_type,trigger_status,add_time,update_time,glue_updatetime)
SELECT @userGroup,'userAiOutboxDispatch','meng_center','CRON','0 * * * * ?','DO_NOTHING','FIRST','userAiOutboxDispatch','DISCARD_LATER',55,1,'BEAN',1,NOW(),NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM xxl_job_info WHERE job_group=@userGroup AND executor_handler='userAiOutboxDispatch');
"@
    $sql+=@"
INSERT INTO xxl_job_group(app_name,name,address_type,access_token,update_time)
VALUES('meng-companion','萌萌服务',0,CONVERT(0x${tokenHex} USING utf8mb4),NOW())
ON DUPLICATE KEY UPDATE access_token=VALUES(access_token),update_time=NOW();
SET @companionGroup=(SELECT id FROM xxl_job_group WHERE app_name='meng-companion');
"@
    foreach($job in @(@{handler='companionOutboxDispatch';cron='0 * * * * ?';timeout=55},@{handler='companionMemoryMaintain';cron='0 */10 * * * ?';timeout=500},@{handler='companionPromiseNotify';cron='0 */5 * * * ?';timeout=240})) {
        $handler=$job.handler;$cron=$job.cron;$timeout=$job.timeout
        $sql+=@"
INSERT INTO xxl_job_info(job_group,name,author,schedule_type,schedule_conf,misfire_strategy,executor_route_strategy,executor_handler,executor_block_strategy,executor_timeout,executor_fail_retry_count,glue_type,trigger_status,add_time,update_time,glue_updatetime)
SELECT @companionGroup,'${handler}','meng_center','CRON','${cron}','DO_NOTHING','FIRST','${handler}','DISCARD_LATER',${timeout},1,'BEAN',1,NOW(),NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM xxl_job_info WHERE job_group=@companionGroup AND executor_handler='${handler}');
UPDATE xxl_job_info SET schedule_conf='${cron}',executor_timeout=${timeout},trigger_status=1,update_time=NOW()
WHERE job_group=@companionGroup AND executor_handler='${handler}';
"@
    }
    $sql | & docker exec -i -e MYSQL_PWD $container mysql -uroot
    if($LASTEXITCODE -ne 0) {throw 'XXL tasks initialization failed.'}
} finally { $env:MYSQL_PWD=$priorPassword;$sql=$null;$passwordHex=$null;$adminHex=$null;$tokenHex=$null }
& docker @arguments up -d --no-deps xxl-job xxl-job-bridge xxl-job-user-bridge xxl-job-companion-bridge
if($LASTEXITCODE -ne 0) {throw 'XXL scheduler startup failed.'}
Write-Output 'XXL scheduler initialized; application database and domain maintenance tasks ready.'
