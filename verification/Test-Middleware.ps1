$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Nacos.ps1"
Import-MengEnvironment
$composeArguments = Get-MengComposeArguments
$ports = Get-MengPorts
$report = [ordered]@{ project = 'meng_center'; checkedAt = [DateTimeOffset]::Now.ToString('o'); passed = $false; checks = @() }

function Add-Check([string] $Name, [bool] $Passed) {
    $script:report.checks += [ordered]@{ name = $Name; passed = $Passed }
    Write-Output "$Name : $Passed"
    if (-not $Passed) { throw "Verification failed: $Name" }
}

try {
    $services = @(& docker @composeArguments ps --format json | ForEach-Object { $_ | ConvertFrom-Json })
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect middleware.' }
    Add-Check 'Four containers healthy' ($services.Count -eq 4 -and @($services | Where-Object Health -NE 'healthy').Count -eq 0)

    $query = & docker @composeArguments exec -T mysql sh -c 'MYSQL_PWD="$MENG_MYSQL_USER_PASSWORD" mysql -h 127.0.0.1 -u meng_user_app meng_user -Nse "SELECT DATABASE(), CURRENT_USER(), 1"' 2>$null
    Add-Check 'MySQL application account executes real query' ($LASTEXITCODE -eq 0 -and $query -match '^meng_user\s+meng_user_app@%\s+1$')
    $grants = & docker @composeArguments exec -T mysql sh -c 'MYSQL_PWD="$MENG_MYSQL_USER_PASSWORD" mysql -h 127.0.0.1 -u meng_user_app -Nse "SHOW GRANTS"' 2>$null
    Add-Check 'MySQL grants limited to own database' ($LASTEXITCODE -eq 0 -and @($grants).Count -eq 2 -and @($grants | Where-Object { $_ -match '^GRANT ALL PRIVILEGES ON `meng_user`\.\*' }).Count -eq 1)
    $null = & docker @composeArguments exec -T mysql sh -c 'MYSQL_PWD="$MENG_MYSQL_USER_PASSWORD" mysql -h 127.0.0.1 -u meng_user_app -Nse "SELECT User FROM mysql.user LIMIT 1"' 2>$null
    Add-Check 'MySQL cross-database access denied' ($LASTEXITCODE -ne 0)

    $redis = & docker @composeArguments exec -T redis sh -c 'REDISCLI_AUTH="$MENG_REDIS_PASSWORD" redis-cli ping' 2>$null
    Add-Check 'Redis authenticated request' ($LASTEXITCODE -eq 0 -and $redis -eq 'PONG')
    $anonymous = & docker @composeArguments exec -T redis redis-cli ping 2>$null
    Add-Check 'Redis anonymous request denied' (($anonymous -join "`n") -match 'NOAUTH')

    $credential = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${env:MENG_RABBITMQ_USERNAME}:${env:MENG_RABBITMQ_PASSWORD}"))
    try {
        $rabbitUser = Invoke-RestMethod -Uri "http://127.0.0.1:$($ports.MENG_RABBITMQ_MANAGEMENT_PORT)/api/whoami" -Headers @{ Authorization = "Basic $credential" } -TimeoutSec 10
        $rabbitVhost = Invoke-RestMethod -Uri "http://127.0.0.1:$($ports.MENG_RABBITMQ_MANAGEMENT_PORT)/api/vhosts/meng" -Headers @{ Authorization = "Basic $credential" } -TimeoutSec 10
    } catch { throw 'RabbitMQ authenticated management request failed.' }
    Add-Check 'RabbitMQ application login and vhost' ($rabbitUser.name -eq $env:MENG_RABBITMQ_USERNAME -and $rabbitVhost.name -eq 'meng')

    $headers = Get-MengNacosHeaders
    $namespaces = Invoke-MengNacosRequest 'Get' '/v3/console/core/namespace/list' @{} $headers
    Add-Check 'Nacos login and namespace' ($namespaces.code -eq 0 -and @($namespaces.data | Where-Object namespace -EQ $env:MENG_NACOS_NAMESPACE).Count -eq 1)

    $report['passed'] = $true
} finally {
    $outputDirectory = Join-Path $PSScriptRoot 'test-output'
    $null = New-Item -ItemType Directory -Force -Path $outputDirectory
    $report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outputDirectory 'middleware.json') -Encoding utf8
}
