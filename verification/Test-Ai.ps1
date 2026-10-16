param([switch]$EvaluateModels,[string]$Baseline,[switch]$Integration,[switch]$Covers)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach($entry in (Get-MengPorts).GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key,[string]$entry.Value,'Process') }
Push-Location (Join-Path $PSScriptRoot '../python_ai')
try {
    & uv sync --frozen --python 3.12
    if($LASTEXITCODE -ne 0){throw 'AI dependency validation failed.'}
    & uv run --frozen ruff check app tests evals
    if($LASTEXITCODE -ne 0){throw 'AI lint failed.'}
    & uv run --frozen pytest -q
    if($LASTEXITCODE -ne 0){throw 'AI tests failed.'}
    if($EvaluateModels){
        $taskEvalArgs=@('run','--frozen','python','-m','evals.run')
        if($Baseline){$taskEvalArgs+=@('--baseline',$Baseline)}
        & uv @taskEvalArgs
        if($LASTEXITCODE -ne 0){throw 'AI evaluation failed or regressed.'}
    }
} finally {Pop-Location}
if($Integration){
    $taskIntegrationArgs=@("$PSScriptRoot/functional/ai.mjs")
    if($Covers){$taskIntegrationArgs+='--covers'}
    & node @taskIntegrationArgs
    if($LASTEXITCODE -ne 0){throw 'AI integration failed.'}
}
