param([ValidateSet('functional', 'load')] [string] $Check = 'functional')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
$ports = Get-MengPorts
foreach ($port in $ports.GetEnumerator()) { [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process') }
foreach ($port in @(18101, 18081, 25250)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Verification port $port is occupied." }
}
$repository = Split-Path $PSScriptRoot -Parent
$output = Join-Path $PSScriptRoot 'test-output'
$null = New-Item -ItemType Directory -Force -Path $output
$vhost = 'meng-verification-' + [Guid]::NewGuid().ToString('N')
$authorization = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($env:MENG_RABBITMQ_USERNAME):$($env:MENG_RABBITMQ_PASSWORD)"))
$headers = @{ Authorization = "Basic $authorization" }
$rabbit = "http://127.0.0.1:$($ports.MENG_RABBITMQ_MANAGEMENT_PORT)/api"
$created = $false
$processes = @()
function Invoke-VerificationRabbit([string] $Method, [string] $Path, [string] $Body = '{}') {
    $result = Invoke-WebRequest -Uri "$rabbit$Path" -Method $Method -Headers $headers -ContentType 'application/json' -Body $Body -SkipHttpErrorCheck
    if ($result.StatusCode -ge 300) { throw 'Verification RabbitMQ setup failed.' }
}
try {
    Invoke-VerificationRabbit 'Put' "/vhosts/$vhost"
    $created = $true
    Invoke-VerificationRabbit 'Put' "/permissions/$vhost/$($env:MENG_RABBITMQ_USERNAME)" '{"configure":".*","write":".*","read":".*"}'
    $java = (Get-Command java).Source
    foreach ($service in @('meng-user', 'meng-gateway')) {
        $jar = Join-Path $repository "java_backend/$service/target/$service-0.1.0-SNAPSHOT.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend before verification.' }
        $arguments = @('-Dfile.encoding=UTF-8', '-jar', $jar, "--spring.application.name=verification-$service", "--spring.rabbitmq.virtual-host=$vhost")
        if ($service -eq 'meng-user') {
            $arguments += @('--server.port=18101', '--meng.user.legal.published=true', '--spring.mail.host=127.0.0.1', '--spring.mail.port=25250', '--spring.mail.properties.mail.smtp.ssl.enable=false', '--spring.mail.properties.mail.smtp.auth=false')
        } else {
            $arguments += @('--server.port=18081', '--spring.cloud.gateway.server.webflux.routes[0].id=verification-user', '--spring.cloud.gateway.server.webflux.routes[0].uri=lb://verification-meng-user', '--spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/user/**')
        }
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output "functional-$service.log") -RedirectStandardError (Join-Path $output "functional-$service.err.log") -PassThru
        $processes += $process
    }
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($processes | Where-Object HasExited) { throw 'A verification service failed to start.' }
        try {
            $user = Invoke-WebRequest -Uri 'http://127.0.0.1:18101/user/internal/users?ids=1' -SkipHttpErrorCheck -TimeoutSec 2
            $gateway = Invoke-WebRequest -Uri 'http://127.0.0.1:18081/user/me' -SkipHttpErrorCheck -TimeoutSec 2
            if ($user.StatusCode -eq 200 -and $gateway.StatusCode -eq 401) { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Verification service startup timed out.' }
    $env:MENG_VERIFICATION_GATEWAY = 'http://127.0.0.1:18081'
    $env:MENG_VERIFICATION_VHOST = $vhost
    & "$PSScriptRoot/Invoke-NodeCheck.ps1" -Check $Check
} finally {
    foreach ($process in $processes) { if (-not $process.HasExited) { Stop-Process -Id $process.Id } }
    if ($created) {
        $result = Invoke-WebRequest -Uri "$rabbit/vhosts/$vhost" -Method Delete -Headers $headers -SkipHttpErrorCheck
        if ($result.StatusCode -ge 300) { throw 'Verification vhost cleanup failed.' }
    }
    [Environment]::SetEnvironmentVariable('MENG_VERIFICATION_GATEWAY', $null, 'Process')
    [Environment]::SetEnvironmentVariable('MENG_VERIFICATION_VHOST', $null, 'Process')
}
