param([switch] $CheckSchedulerFailure)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
$ports = Get-MengPorts
foreach ($entry in $ports.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process') }
$compose = Get-MengComposeArguments
$mysql = & docker @compose ps --quiet mysql
$scheduler = & docker @compose ps --quiet xxl-job
$bridge = & docker @compose ps --quiet xxl-job-bridge
$output = Join-Path $PSScriptRoot 'test-output/content-jobs'
$null = New-Item -ItemType Directory -Force -Path $output
$report = [ordered]@{passed=$false; checks=@()}
$previousPassword = $env:MYSQL_PWD
$previousToken = $env:MENG_XXL_JOB_ACCESS_TOKEN
$isolated = $null
$stopped = $false

function Query([string]$Sql) {
    $result = $Sql | & docker exec -i -e MYSQL_PWD $mysql mysql -umeng_xxl_job_app xxl_job -N -B
    if ($LASTEXITCODE -ne 0) { throw 'Scheduler database query failed.' }
    return $result
}
function Check([string]$Name, [bool]$Passed) {
    if (-not $Passed) { throw "Verification failed: $Name" }
    $script:report.checks += $Name
    Write-Output "PASS $Name"
}
function Poll([scriptblock]$Condition, [int]$Seconds=45) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do { if (& $Condition) { return }; Start-Sleep -Milliseconds 500 } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Verification polling timed out.'
}
function Start-Isolated([string]$Name) {
    if (Get-NetTCPConnection -State Listen -LocalPort 18102,19102 -ErrorAction SilentlyContinue) { throw 'Isolation ports 18102/19102 are already occupied.' }
    $repository = Split-Path $PSScriptRoot -Parent
    $jar = Join-Path $repository 'java_backend/meng-content/target/meng-content-0.1.0-SNAPSHOT.jar'
    return Start-Process java -WindowStyle Hidden -PassThru -ArgumentList @('-Xms64m','-Xmx384m','-XX:MaxMetaspaceSize=256m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-jar',$jar,'--server.port=18102','--spring.cloud.nacos.discovery.register-enabled=false','--meng.content.job.port=19102','--meng.content.job.address=http://127.0.0.1:19102/') -RedirectStandardOutput "$output/$Name.log" -RedirectStandardError "$output/$Name-error.log"
}

try {
    $env:MYSQL_PWD = $env:MENG_MYSQL_XXLJOB_PASSWORD
    $jobs = @(Query "SELECT id,executor_handler FROM xxl_job_info WHERE executor_handler IN ('contentHotRebuild','contentSearchRebuild','contentAiReanalyzeFailed') ORDER BY id;")
    Check 'Exactly three enabled tasks with specified cron, timeouts, strategies and retries' ($jobs.Count -eq 3 -and (Query "SELECT COUNT(*) FROM xxl_job_info WHERE ((executor_handler='contentHotRebuild' AND schedule_conf='0 0 4 * * ?' AND executor_timeout=600) OR (executor_handler='contentSearchRebuild' AND schedule_conf='0 10 4 * * ?' AND executor_timeout=1800) OR (executor_handler='contentAiReanalyzeFailed' AND schedule_conf='0 0 * * * ?' AND executor_timeout=180)) AND schedule_type='CRON' AND trigger_status=1 AND executor_route_strategy='FIRST' AND executor_block_strategy='DISCARD_LATER' AND executor_fail_retry_count=1 AND misfire_strategy='DO_NOTHING';") -eq '3')
    Poll { (Query "SELECT COUNT(*) FROM xxl_job_group WHERE app_name='meng-content' AND address_list='http://127.0.0.1:$($ports.MENG_CONTENT_JOB_PORT)/';") -eq '1' }
    Check 'Executor auto-registers with localhost address' $true
    $executor = "http://127.0.0.1:$($ports.MENG_CONTENT_JOB_PORT)/beat"
    $denied = Invoke-RestMethod $executor -Method Post -Body '{}' -ContentType 'application/json'
    $allowed = Invoke-RestMethod $executor -Method Post -Body '{}' -ContentType 'application/json' -Headers @{'XXL-JOB-ACCESS-TOKEN'=$env:MENG_XXL_JOB_ACCESS_TOKEN;'XXL-JOB-APPNAME'='meng-content'}
    Check 'Executor rejects anonymous requests and accepts environment token' ($denied.code -ne 200 -and $allowed.code -eq 200)
    $login = Invoke-RestMethod "http://127.0.0.1:$($ports.MENG_XXL_JOB_PORT)/auth/doLogin" -Method Post -Body @{userName='admin';password=$env:MENG_XXL_JOB_ADMIN_PASSWORD} -SessionVariable session
    Check 'Scheduler initialized administrator authenticates' ($login.code -eq 200)
    foreach ($job in $jobs) {
        $parts = $job -split "`t"
        $jobId = [int]$parts[0]
        $last = [long](Query 'SELECT COALESCE(MAX(id),0) FROM xxl_job_log;')
        $trigger = Invoke-RestMethod "http://127.0.0.1:$($ports.MENG_XXL_JOB_PORT)/jobinfo/trigger" -Method Post -WebSession $session -Body @{id=$jobId;executorParam='';addressList=''}
        Check "$($parts[1]) manual trigger accepted" ($trigger.code -eq 200)
        Poll { (Query "SELECT COUNT(*) FROM xxl_job_log WHERE id>$last AND job_id=$jobId AND trigger_code=200 AND handle_code=200;") -ge 1 } 90
        Check "$($parts[1]) executes and reports success through scheduler" $true
    }
    foreach ($invalid in @('', 'default_token')) {
        $env:MENG_XXL_JOB_ACCESS_TOKEN = $invalid
        $isolated = Start-Isolated 'invalid-token'
        Poll { $isolated.Refresh(); $isolated.HasExited } 60
        $log = Get-Content "$output/invalid-token.log" -Raw
        Check 'Invalid environment token fails service startup with explicit message' ($isolated.ExitCode -ne 0 -and $log.Contains('MENG_XXL_JOB_ACCESS_TOKEN must be set'))
        $isolated = $null
    }
    $env:MENG_XXL_JOB_ACCESS_TOKEN = $previousToken
    if ($CheckSchedulerFailure) {
        $null = & docker stop $scheduler
        if ($LASTEXITCODE -ne 0) { throw 'Scheduler could not be stopped.' }
        $stopped = $true
        $isolated = Start-Isolated 'scheduler-offline'
        Poll { try { (Invoke-RestMethod 'http://127.0.0.1:18102/actuator/health' -TimeoutSec 2).status -eq 'UP' } catch { $false } } 60
        Check 'Content service starts while scheduler is offline' $true
        Check 'Existing content service remains healthy while scheduler is offline' ((Invoke-RestMethod 'http://127.0.0.1:8102/actuator/health').status -eq 'UP')
        $probeId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
        try {
            $identity = @{'X-User-Id'=$probeId;'X-User-Role'='USER'}
            $hot = Invoke-RestMethod 'http://127.0.0.1:18102/content/posts?sort=HOT&size=1' -Headers $identity
            $search = Invoke-RestMethod 'http://127.0.0.1:18102/content/search/posts?q=xxlverification&size=1' -Headers $identity
            Check 'Isolated hot feed and search respond while scheduler is offline' ($hot.code -eq 'OK' -and $search.code -eq 'OK')
        } finally {
            $redisContainer = & docker @compose ps --quiet redis
            $null = & docker exec $redisContainer sh -c "REDISCLI_AUTH=`"`$MENG_REDIS_PASSWORD`" redis-cli DEL meng:content:limit:search:$probeId"
        }
    }
    $report.passed = $true
} finally {
    if ($isolated -and -not $isolated.HasExited) { Stop-Process -Id $isolated.Id -Force }
    $env:MYSQL_PWD = $previousPassword
    $env:MENG_XXL_JOB_ACCESS_TOKEN = $previousToken
    if ($stopped) {
        $null = & docker start $scheduler
        if ($LASTEXITCODE -ne 0) { throw 'Scheduler restoration failed.' }
        $null = & docker restart $bridge
        if ($LASTEXITCODE -ne 0) { throw 'Scheduler internal bridge restoration failed.' }
    }
    $report | ConvertTo-Json -Depth 4 | Set-Content "$output/result.json" -Encoding utf8
}
