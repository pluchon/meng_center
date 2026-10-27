param(
    [ValidateSet('station', 'companion', 'decisions')][string]$Task,
    [ValidateRange(1,2147483647)][int]$TokenBudget,
    [ValidatePattern('^[a-zA-Z0-9._-]{1,100}$')][string]$BudgetId,
    [string[]]$EvaluationArguments = @()
)
$ErrorActionPreference = 'Stop'
if (-not $Task -or -not $TokenBudget -or -not $BudgetId) { throw 'Task, TokenBudget and BudgetId are required.' }
if ($Task -eq 'companion') {
    if ('--smoke' -notin $EvaluationArguments -and '--acceptance' -notin $EvaluationArguments) {
        throw 'Companion evaluations require --smoke or explicitly authorized --acceptance.'
    }
    if ('--phase-id' -notin $EvaluationArguments) { throw 'Companion evaluations require a fixed --phase-id.' }
} elseif ('--acceptance' -notin $EvaluationArguments) {
    throw 'Batch model evaluations require explicitly authorized --acceptance.'
}
if ('--phase-id' -notin $EvaluationArguments) { throw 'Evaluations require a fixed --phase-id.' }
$repository = Split-Path $PSScriptRoot -Parent
. "$repository/deploy/scripts/Environment.ps1"
Import-MengEnvironment
foreach ($entry in (Get-MengPorts).GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
}
$env:PYTHONUTF8 = '1'
$env:OMP_NUM_THREADS = '1'
$env:OPENBLAS_NUM_THREADS = '1'
$env:MKL_NUM_THREADS = '1'
$module = switch ($Task) { station { 'evals.run' } companion { 'evals.companion.select' } decisions { 'evals.companion.decisions' } }
$outputDirectory = Join-Path $PSScriptRoot 'test-output'
$null = New-Item -ItemType Directory -Force -Path $outputDirectory
$prefix = Join-Path $outputDirectory "evaluation-$([guid]::NewGuid().ToString('N'))"
$child = $null
$mutex = [System.Threading.Mutex]::new($false, 'Local\MengModelEvaluation')
$owned = $false
function Assert-Resources {
    $memory = Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory
    if ($memory.AvailableMBytes -lt 3072 -or $memory.PercentCommittedBytesInUse -gt 85) {
        throw "Evaluation resource limit: free=$($memory.AvailableMBytes)MB; committed=$($memory.PercentCommittedBytesInUse)%."
    }
    return $memory
}
try {
    try { $owned = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) { throw 'Another guarded model evaluation is already running.' }
    $null = Assert-Resources
    $arguments = @('-u', '-m', $module, '--token-budget', [string]$TokenBudget, '--budget-id', $BudgetId) + $EvaluationArguments
    $quotedArguments = $arguments | ForEach-Object { '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"' }
    $child = Start-Process "$repository/python_ai/.venv/Scripts/python.exe" -WindowStyle Hidden -PassThru -WorkingDirectory "$repository/python_ai" -ArgumentList $quotedArguments -RedirectStandardOutput "$prefix.log" -RedirectStandardError "$prefix.err.log"
    $samples = 0
    while (-not $child.WaitForExit(15000)) {
        $memory = Assert-Resources
        if (++$samples % 4 -eq 0) {
            Write-Output "Evaluation: free=$($memory.AvailableMBytes)MB; committed=$($memory.PercentCommittedBytesInUse)%."
            Get-Content -LiteralPath "$prefix.log" -Tail 1
        }
    }
    Get-Content -LiteralPath "$prefix.log" -Tail 15
    if ($child.ExitCode -ne 0) {
        Get-Content -LiteralPath "$prefix.err.log" -Tail 15
        throw "Model evaluation failed with exit code $($child.ExitCode)."
    }
} finally {
    if ($child) {
        # 虚拟环境启动器另有解释器子进程；只停止本次启动的评测。
        $workers = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($child.Id)"
        foreach ($worker in $workers) {
            if ($worker.Name -eq 'python.exe' -and $worker.CommandLine.Contains($module)) {
                Stop-Process -Id $worker.ProcessId -ErrorAction SilentlyContinue
            }
        }
        if (-not $child.HasExited) { Stop-Process -Id $child.Id -ErrorAction SilentlyContinue }
    }
    Remove-Item -LiteralPath "$prefix.log", "$prefix.err.log" -ErrorAction SilentlyContinue
    if ($owned) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
