param([switch] $Production)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process')
}
$previousProduction = $env:MENG_GROWTH_PRODUCTION
try {
    $env:MENG_GROWTH_PRODUCTION = if ($Production) { '1' } else { '0' }
    & node "$PSScriptRoot/functional/growth-center.mjs"
    if ($LASTEXITCODE -ne 0) { throw 'Growth center verification failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('MENG_GROWTH_PRODUCTION', $previousProduction, 'Process')
}
