param([switch]$SkipSync)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
foreach($entry in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key,[string]$entry.Value,'Process')
}
if([string]::IsNullOrWhiteSpace($env:DASHSCOPE_API_KEY)) {throw 'DASHSCOPE_API_KEY is required.'}
$taskAiRoot=Join-Path $PSScriptRoot '../../python_ai'
Push-Location $taskAiRoot
try {
    if(-not $SkipSync) { & uv sync --frozen --python 3.12; if($LASTEXITCODE -ne 0){throw 'AI dependencies unavailable.'} }
    $env:LANGSMITH_TRACING='false'
    & uv run --frozen python -m uvicorn app.main:app --host 127.0.0.1 --port 8106 --no-access-log
} finally {Pop-Location}
