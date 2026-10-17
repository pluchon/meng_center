param([switch]$EvaluateModels, [string]$CompareModel, [string]$Baseline,
    [ValidateSet('TSUNDERE', 'GENKI', 'NEKO')][string]$Character, [string]$MavenPath)
$ErrorActionPreference = 'Stop'
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
    & uv run --frozen pytest tests/test_companion.py tests/test_companion_runtime.py -q
    if ($LASTEXITCODE -ne 0) { throw 'Companion graph tests failed.' }
    if ($EvaluateModels) {
        $arguments = @('run', '--frozen', 'python', '-m', 'evals.companion.run')
        if ($CompareModel) { $arguments += @('--compare-model', $CompareModel) }
        if ($Baseline) { $arguments += @('--baseline', $Baseline) }
        if ($Character) { $arguments += @('--character', $Character) }
        & uv @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Companion model evaluation failed.' }
    }
} finally { Pop-Location }
