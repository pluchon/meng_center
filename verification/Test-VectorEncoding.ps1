$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
& node "$PSScriptRoot/runtime/vector-status.mjs"
if ($LASTEXITCODE -ne 0) { throw 'Vector encoding inspection failed.' }
