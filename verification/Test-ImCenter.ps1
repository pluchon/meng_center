param([switch] $Production)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($entry in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key,[string]$entry.Value,'Process') }
$previous=$env:MENG_IM_PRODUCTION
try {
    $env:MENG_IM_PRODUCTION=if($Production){'1'}else{'0'}
    & node "$PSScriptRoot/functional/im-center.mjs"
    if($LASTEXITCODE -ne 0) { throw 'Message center verification failed.' }
} finally { [Environment]::SetEnvironmentVariable('MENG_IM_PRODUCTION',$previous,'Process') }
