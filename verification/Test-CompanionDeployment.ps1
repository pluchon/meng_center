param([switch]$AllowPausedMemoryMaintenance)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
$repoRoot=Split-Path $PSScriptRoot -Parent
$catalog=Get-Content -LiteralPath "$repoRoot/java_backend/meng-companion/src/main/resources/companion/catalog.json" -Raw | ConvertFrom-Json
$catalogVersion=$catalog.version
if($catalogVersion -notmatch '^[a-zA-Z0-9._-]{1,80}$'){throw 'Invalid companion catalog version.'}
$emotions=Get-Content -LiteralPath "$repoRoot/java_backend/meng-content/src/main/resources/emotion/catalog.json" -Raw | ConvertFrom-Json
$emotionVersion=$emotions.version
if($emotionVersion -notmatch '^[a-zA-Z0-9._-]{1,80}$'){throw 'Invalid emotion catalog version.'}
foreach($service in @(@{name='meng-im';port=8104},@{name='meng-companion';port=8107},@{name='meng-ai';port=8106})){
    $health=Invoke-RestMethod "http://127.0.0.1:$($service.port)/actuator/health" -TimeoutSec 5
    if($health.status -ne 'UP'){throw "$($service.name) is not healthy."}
    $listener=Get-NetTCPConnection -LocalPort $service.port -State Listen | Select-Object -First 1
    $runtime=Get-Process -Id $listener.OwningProcess
    $processInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$($runtime.Id)"
    if($service.name -ne 'meng-ai'){
        $artifact=Get-Item -LiteralPath "$repoRoot/java_backend/$($service.name)/target/$($service.name)-0.1.0-SNAPSHOT.jar"
        if($processInfo.CommandLine -notmatch [regex]::Escape($artifact.Name)){throw "Unexpected process on $($service.port)."}
        if($runtime.StartTime -lt $artifact.LastWriteTime){throw "$($service.name) still runs an older JAR; restart required."}
    } else {
        if($processInfo.CommandLine -notmatch 'uvicorn.*app.main:app'){throw 'Unexpected AI process on 8106.'}
        $sources=@(& rg --files "$repoRoot/python_ai/app" -g '*.py')
        $latestSource=$sources | ForEach-Object {Get-Item -LiteralPath $_} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if($runtime.StartTime -lt $latestSource.LastWriteTime){throw 'meng-ai source changed after startup; restart required.'}
    }
    Write-Output "$($service.name) healthy on $($service.port), process $($runtime.Id)."
}
$executor=Get-NetTCPConnection -LocalPort 9107 -State Listen
if(-not $executor){throw 'Companion job executor is not listening on 9107.'}
$composeArgs=Get-MengComposeArguments
$mysqlId=& docker @composeArgs ps --quiet mysql
if(-not $mysqlId){throw 'Project MySQL container is unavailable.'}
$priorPassword=$env:MYSQL_PWD
$maintenanceStatus = if ($AllowPausedMemoryMaintenance) { 'IN (0,1)' } else { '=1' }
try{
    $env:MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD
    $sql=@"
SELECT IF((SELECT COUNT(*) FROM meng_companion.flyway_schema_history WHERE version IN ('20261017002','20261017003','20261023001','20261023002','20261023003','20261025001','20261027001','20261027002','20261027003','20261027004') AND success=1)=10,'OK','FAIL companion migrations');
SELECT IF((SELECT COUNT(*) FROM meng_im.flyway_schema_history WHERE version='20261027001' AND success=1)=1,'OK','FAIL IM migration');
SELECT IF((SELECT COUNT(*) FROM meng_companion.companion_card WHERE version='${catalogVersion}' AND active=1)=1,'OK','FAIL catalog cards');
SELECT IF((SELECT COUNT(*) FROM meng_companion.companion_card old JOIN meng_companion.companion_card current_card ON current_card.character_code=old.character_code AND current_card.id>old.id WHERE old.active=1 AND current_card.active=1 AND current_card.delete_state=0)=0,'OK','FAIL stale active cards');
SELECT IF((SELECT COUNT(*) FROM meng_companion.companion_line WHERE character_code='NEKO' AND version='${catalogVersion}' AND enabled=1 AND delete_state=0)>=3,'OK','FAIL scene coverage');
SELECT IF((SELECT COUNT(DISTINCT j.executor_handler) FROM xxl_job.xxl_job_info j JOIN xxl_job.xxl_job_group g ON g.id=j.job_group WHERE g.app_name='meng-companion' AND ((j.executor_handler='companionOutboxDispatch' AND j.trigger_status=1) OR (j.executor_handler='companionMemoryMaintain' AND j.trigger_status $maintenanceStatus)))=2 AND (SELECT COUNT(*) FROM xxl_job.xxl_job_info j JOIN xxl_job.xxl_job_group g ON g.id=j.job_group WHERE g.app_name='meng-companion' AND j.trigger_status=1 AND j.executor_handler NOT IN ('companionOutboxDispatch','companionMemoryMaintain'))=0,'OK','FAIL companion jobs');
SELECT IF((SELECT COUNT(*) FROM xxl_job.xxl_job_registry WHERE registry_group='EXECUTOR' AND registry_key='meng-companion' AND update_time>DATE_SUB(NOW(),INTERVAL 90 SECOND))>0,'OK','FAIL executor registry');
SELECT IF((SELECT COUNT(*) FROM meng_content.flyway_schema_history WHERE version='20261023001' AND success=1)=1,'OK','FAIL content runtime migration');
SELECT IF((SELECT COUNT(*) FROM meng_content.emotion WHERE catalog_version='${emotionVersion}' AND delete_state=0)=24,'OK','FAIL emotion catalog');
"@
    $checks=@($sql | & docker exec -i -e MYSQL_PWD $mysqlId mysql -uroot --batch --skip-column-names)
    if($LASTEXITCODE -ne 0){throw 'Deployment database assertions could not execute.'}
    if(@($checks | Where-Object {$_ -ne 'OK'}).Count -or $checks.Count -ne 9){throw "Deployment assertions failed: $($checks -join ', ')"}
    Write-Output "Flyway migrations, catalog $catalogVersion, Naitang scenes and registered jobs verified."
    if ($AllowPausedMemoryMaintenance) { Write-Output 'Memory maintenance may remain paused for offline development; this check does not enable it.' }
} finally {$env:MYSQL_PWD=$priorPassword}
