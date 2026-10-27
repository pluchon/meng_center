param([switch]$EvaluateModels,[string]$Baseline,[switch]$Integration,[switch]$Covers,[int]$EvaluationTokenBudget=0,[string]$EvaluationBudgetId,[switch]$Acceptance,[string]$EvaluationPhaseId)
$ErrorActionPreference = 'Stop'
if($EvaluateModels -and ($EvaluationTokenBudget -le 0 -or $EvaluationBudgetId -notmatch '^[a-zA-Z0-9._-]{1,100}$')) { throw 'Real model evaluations require an authorized token budget and fixed budget id.' }
if($EvaluateModels -and -not $Acceptance) { throw 'Batch model evaluations require explicit Acceptance.' }
if($EvaluateModels -and $EvaluationPhaseId -notmatch '^[a-zA-Z0-9._-]{1,100}$') { throw 'EvaluationPhaseId is required.' }
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
        $taskEvalArgs=@('--acceptance','--phase-id',$EvaluationPhaseId)
        if($Baseline){$taskEvalArgs+=@('--baseline',$Baseline)}
        & "$PSScriptRoot/Invoke-ModelEvaluation.ps1" -Task station -TokenBudget $EvaluationTokenBudget -BudgetId $EvaluationBudgetId -EvaluationArguments $taskEvalArgs
    }
} finally {Pop-Location}
if($Integration){
    $taskIntegrationArgs=@("$PSScriptRoot/functional/ai.mjs")
    if($Covers){$taskIntegrationArgs+='--covers'}
    & node @taskIntegrationArgs
    if($LASTEXITCODE -ne 0){throw 'AI integration failed.'}
}
