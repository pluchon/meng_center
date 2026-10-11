$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process') }
$deadline = [DateTime]::UtcNow.AddSeconds(60)
do {
    $ready = $true
    foreach ($port in @(8101,8102,8103)) {
        try { $response = Invoke-WebRequest "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2 -SkipHttpErrorCheck; if ($response.StatusCode -ne 200) { $ready = $false } }
        catch { $ready = $false }
    }
    if (-not $ready) { Start-Sleep -Milliseconds 500 }
} while (-not $ready -and [DateTime]::UtcNow -lt $deadline)
if (-not $ready) { throw 'Start the three domain services before verification.' }
& node "$PSScriptRoot/functional/home-growth.mjs"
if ($LASTEXITCODE -ne 0) { throw 'Home/growth verification failed.' }
