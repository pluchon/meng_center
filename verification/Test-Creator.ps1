param([switch] $Production)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process')
}
$previousProduction = $env:MENG_CREATOR_PRODUCTION
try {
    $env:MENG_CREATOR_PRODUCTION = if ($Production) { '1' } else { '0' }
    & node "$PSScriptRoot/functional/creator.mjs"
    if ($LASTEXITCODE -ne 0) { throw 'Creator verification failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('MENG_CREATOR_PRODUCTION', $previousProduction, 'Process')
}
