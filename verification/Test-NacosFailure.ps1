$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process') }
$compose = Get-MengComposeArguments
$repository = Split-Path $PSScriptRoot -Parent
$output = Join-Path $PSScriptRoot 'test-output'
$report = [ordered]@{ checkedAt = [DateTimeOffset]::Now.ToString('o'); passed = $false; cases = @(); middlewareRestored = $false }
$process = $null
try {
    & docker @compose stop nacos | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the project Nacos container.' }
    foreach ($service in @('meng-user', 'meng-gateway')) {
        $arguments = @('-Dfile.encoding=UTF-8', '-jar', (Join-Path $repository "java_backend/$service/target/$service-0.1.0-SNAPSHOT.jar"), '--server.port=0', '--spring.cloud.nacos.discovery.enabled=false')
        $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output "nacos-offline-$service.log") -RedirectStandardError (Join-Path $output "nacos-offline-$service.err.log") -PassThru
        if (-not $process.WaitForExit(45000)) { Stop-Process -Id $process.Id; throw "$service did not exit within the outage verification limit." }
        $report.cases += @{ service = $service; exitCode = $process.ExitCode; passed = $process.ExitCode -ne 0 }
        if ($process.ExitCode -eq 0) { throw "$service started despite the Nacos outage." }
    }
    $report.passed = $true
} finally {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id }
    & docker @compose start nacos | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = & docker @compose ps --format json nacos | ConvertFrom-Json
        if ($state.Health -eq 'healthy') { $report.middlewareRestored = $true; break }
        Start-Sleep -Milliseconds 500
    }
    $report | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8NoBOM (Join-Path $output 'nacos-offline-report.json')
    if (-not $report.middlewareRestored) { throw 'Nacos did not recover its health status.' }
}
$report | ConvertTo-Json -Depth 6
