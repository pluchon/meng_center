param([ValidateSet('functional', 'browser', 'load')] [string] $Check)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process')
}
& node (Join-Path $PSScriptRoot "$Check/check.mjs")
if ($LASTEXITCODE -ne 0) { throw "$Check verification failed; see verification/test-output." }
