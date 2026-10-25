param([switch]$EvaluateModels, [string]$CompareModel, [string]$Baseline,
    [ValidateSet('TSUNDERE', 'GENKI', 'NEKO')][string]$Character, [string]$MavenPath,
    [switch]$SelectMouthModels, [switch]$EvaluateDecisions, [switch]$SmallModelSample, [ValidateRange(0,2147483647)][int]$EvaluationTokenBudget=0,
    [string]$EvaluationBudgetId, [string]$EvaluationOutput)
$ErrorActionPreference = 'Stop'
if (($EvaluateModels -or $SelectMouthModels -or $EvaluateDecisions) -and ($EvaluationTokenBudget -le 0 -or $EvaluationBudgetId -notmatch '^[a-zA-Z0-9._-]{1,100}$')) {
    throw 'Real model evaluations require an explicitly authorized -EvaluationTokenBudget and a fixed -EvaluationBudgetId.'
}
. "$PSScriptRoot/../deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($entry in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
}
if (-not $MavenPath) {
    $MavenPath = Get-ChildItem 'C:/Program Files/JetBrains/IntelliJ IDEA */plugins/maven-plugin/lib/maven3/bin/mvn.cmd' |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $MavenPath) { throw 'Specify the IDEA Maven path with -MavenPath.' }
$priorMavenOptions = $env:MAVEN_OPTS
try {
    if (-not $env:MAVEN_OPTS) { $env:MAVEN_OPTS = '-Xms64m -Xmx384m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2' }
    & $MavenPath -q -f "$PSScriptRoot/../java_backend/pom.xml" -pl meng-companion -am '-DforkCount=1' '-DargLine=-Xms32m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2' test
    if ($LASTEXITCODE -ne 0) { throw 'Companion Java tests failed.' }
} finally { $env:MAVEN_OPTS = $priorMavenOptions }
Push-Location "$PSScriptRoot/../python_ai"
try {
    & uv sync --frozen --python 3.12
    if ($LASTEXITCODE -ne 0) { throw 'AI dependencies unavailable.' }
    & uv run --frozen ruff check app tests evals
    if ($LASTEXITCODE -ne 0) { throw 'AI static checks failed.' }
    & uv run --frozen pytest tests/test_companion.py tests/test_companion_agent.py tests/test_companion_guard.py tests/test_companion_runtime.py tests/test_companion_speculative.py tests/test_companion_selection.py tests/test_companion_review.py tests/test_retrieval.py tests/test_model_routing.py -q
    if ($LASTEXITCODE -ne 0) { throw 'Companion graph tests failed.' }
    if ($EvaluateModels) {
        $arguments = @()
        if ($CompareModel) { $arguments += @('--compare-model', $CompareModel) }
        if ($Baseline) { $arguments += @('--baseline', $Baseline) }
        if ($Character) { $arguments += @('--character', $Character) }
        & "$PSScriptRoot/Invoke-ModelEvaluation.ps1" -Task companion -TokenBudget $EvaluationTokenBudget -BudgetId $EvaluationBudgetId -EvaluationArguments $arguments
    }
    if ($SelectMouthModels) {
        $selectionArguments = @()
        if ($SmallModelSample) { $selectionArguments += '--small-sample' }
        if ($Baseline) { $selectionArguments += @('--baseline', $Baseline) }
        if ($Character) { $selectionArguments += @('--character', $Character) }
        if ($EvaluationOutput) { $selectionArguments += @('--output', $EvaluationOutput) }
        & "$PSScriptRoot/Invoke-ModelEvaluation.ps1" -Task companion -TokenBudget $EvaluationTokenBudget -BudgetId $EvaluationBudgetId -EvaluationArguments $selectionArguments
    }
    if ($EvaluateDecisions) {
        & "$PSScriptRoot/Invoke-ModelEvaluation.ps1" -Task decisions -TokenBudget $EvaluationTokenBudget -BudgetId $EvaluationBudgetId
    }
} finally { Pop-Location }
