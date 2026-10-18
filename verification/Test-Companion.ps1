param([switch]$EvaluateModels, [string]$CompareModel, [string]$Baseline,
    [ValidateSet('TSUNDERE', 'GENKI', 'NEKO')][string]$Character, [string]$MavenPath,
    [switch]$SelectMouthModels, [switch]$SmallModelSample, [ValidateRange(0,2147483647)][int]$EvaluationTokenBudget=0,
    [string]$EvaluationBudgetId)
$ErrorActionPreference = 'Stop'
if (($EvaluateModels -or $SelectMouthModels) -and ($EvaluationTokenBudget -le 0 -or $EvaluationBudgetId -notmatch '^[a-zA-Z0-9._-]{1,100}$')) {
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
& $MavenPath -q -f "$PSScriptRoot/../java_backend/pom.xml" -pl meng-companion -am test
if ($LASTEXITCODE -ne 0) { throw 'Companion Java tests failed.' }
Push-Location "$PSScriptRoot/../python_ai"
try {
    & uv sync --frozen --python 3.12
    if ($LASTEXITCODE -ne 0) { throw 'AI dependencies unavailable.' }
    & uv run --frozen ruff check app tests evals
    if ($LASTEXITCODE -ne 0) { throw 'AI static checks failed.' }
    & uv run --frozen pytest tests/test_companion.py tests/test_companion_runtime.py tests/test_companion_selection.py tests/test_companion_review.py tests/test_retrieval.py -q
    if ($LASTEXITCODE -ne 0) { throw 'Companion graph tests failed.' }
    if ($EvaluateModels) {
        $arguments = @('run', '--frozen', 'python', '-m', 'evals.companion.run')
        $arguments += @('--token-budget',[string]$EvaluationTokenBudget,'--budget-id',$EvaluationBudgetId)
        if ($CompareModel) { $arguments += @('--compare-model', $CompareModel) }
        if ($Baseline) { $arguments += @('--baseline', $Baseline) }
        if ($Character) { $arguments += @('--character', $Character) }
        & uv @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Companion model evaluation failed.' }
    }
    if ($SelectMouthModels) {
        $selectionArguments = @('run','--frozen','python','-m','evals.companion.select')
        if ($SmallModelSample) { $selectionArguments += '--small-sample' }
        $selectionArguments += @('--token-budget',[string]$EvaluationTokenBudget,'--budget-id',$EvaluationBudgetId)
        & uv @selectionArguments
        if ($LASTEXITCODE -ne 0) { throw 'No mouth model passed the selection gates.' }
    }
} finally { Pop-Location }
