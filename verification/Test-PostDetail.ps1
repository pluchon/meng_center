$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($port in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($port.Key, [string] $port.Value, 'Process') }
& node "$PSScriptRoot/functional/post-detail.mjs"
if ($LASTEXITCODE -ne 0) { throw 'Post detail verification failed.' }
