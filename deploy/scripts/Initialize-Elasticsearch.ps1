$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
foreach ($name in @('MENG_ES_ELASTIC_PASSWORD', 'MENG_ES_CONTENT_PASSWORD')) {
    if (-not ([Environment]::GetEnvironmentVariable($name, 'User') -or [Environment]::GetEnvironmentVariable($name, 'Process'))) {
        $value = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        [Environment]::SetEnvironmentVariable($name, $value, 'User')
    }
}
Import-MengEnvironment
$ports = Get-MengPorts
$arguments = Get-MengComposeArguments
$existing = @(& docker @arguments ps --all --quiet elasticsearch)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Elasticsearch container.' }
if ($existing.Count -eq 0) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $ports.MENG_ES_PORT)
    try { $listener.Start() } catch { throw 'Elasticsearch host port is occupied. Update deploy/ports.env.' } finally { $listener.Stop() }
}
& docker @arguments up -d --build elasticsearch
if ($LASTEXITCODE -ne 0) { throw 'Elasticsearch startup failed.' }
$base = "http://127.0.0.1:$($ports.MENG_ES_PORT)"
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("elastic:$($env:MENG_ES_ELASTIC_PASSWORD)")) }
$ready = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    try { $null = Invoke-RestMethod -Uri "$base/_cluster/health?wait_for_status=yellow" -Headers $headers -TimeoutSec 3; $ready = $true; break } catch { Start-Sleep -Seconds 1 }
}
if (-not $ready) { throw 'Elasticsearch readiness timeout.' }
$role = @{ cluster = @(); indices = @(@{ names = @('meng_post_*', 'meng_post', 'meng_vector_post*'); privileges = @('read', 'write', 'manage') }) } | ConvertTo-Json -Depth 6
$null = Invoke-RestMethod -Method Put -Uri "$base/_security/role/meng_content" -Headers $headers -ContentType 'application/json' -Body $role
$account = @{ password = $env:MENG_ES_CONTENT_PASSWORD; roles = @('meng_content') } | ConvertTo-Json
$null = Invoke-RestMethod -Method Put -Uri "$base/_security/user/meng_content_app" -Headers $headers -ContentType 'application/json' -Body $account
Write-Output 'Elasticsearch ready; application role restricted to meng_post indices and alias.'
