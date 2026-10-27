param([string]$EndDay = [DateTime]::UtcNow.ToString('yyyy-MM-dd'), [ValidateRange(1,31)][int]$Days = 7,
    [string]$Output = "$PSScriptRoot/../test-output/ai-runtime-dashboard.html")
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
& node "$PSScriptRoot/runtime/dashboard.mjs" $EndDay $Days $Output
if ($LASTEXITCODE -ne 0) { throw 'AI runtime dashboard export failed.' }
