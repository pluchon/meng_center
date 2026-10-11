param([switch] $CheckElasticsearchFailure)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process') }
$previousFaultCheck = $env:MENG_SEARCH_FAULT_CHECK
try {
    $env:MENG_SEARCH_FAULT_CHECK = if ($CheckElasticsearchFailure) { '1' } else { '0' }
    & node "$PSScriptRoot/functional/search-hot.mjs"
    if ($LASTEXITCODE -ne 0) { throw 'Search and hot rank verification failed.' }
} finally { [Environment]::SetEnvironmentVariable('MENG_SEARCH_FAULT_CHECK', $previousFaultCheck, 'Process') }
