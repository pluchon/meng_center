param([string] $MavenPath)
$ErrorActionPreference = 'Stop'
if (-not $MavenPath) {
    $ideaDirectory = Get-ChildItem 'C:/Program Files/JetBrains' -Directory -Filter 'IntelliJ IDEA *' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $ideaDirectory) { throw 'Set -MavenPath to the IDEA bundled Maven command.' }
    $MavenPath = Join-Path $ideaDirectory.FullName 'plugins/maven-plugin/lib/maven3/bin/mvn.cmd'
}
if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'IDEA Maven command not found.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
& $MavenPath -B -ntp -f (Join-Path $projectRoot 'java_backend/pom.xml') test
if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
$suites = @(Get-ChildItem (Join-Path $projectRoot 'java_backend') -Recurse -File -Filter 'TEST-*.xml' |
    Where-Object FullName -Match '[\\/]target[\\/]surefire-reports[\\/]' | ForEach-Object {
        [xml] $suite = Get-Content -LiteralPath $_.FullName -Raw
        [ordered]@{
            name = [string] $suite.testsuite.name
            tests = [int] $suite.testsuite.tests
            failures = [int] $suite.testsuite.failures
            errors = [int] $suite.testsuite.errors
            skipped = [int] $suite.testsuite.skipped
        }
    })
$outputDirectory = Join-Path $PSScriptRoot 'test-output'
$null = New-Item -ItemType Directory -Force -Path $outputDirectory
[ordered]@{ checkedAt = [DateTimeOffset]::Now.ToString('o'); passed = $true; suites = $suites } |
    ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outputDirectory 'backend.json') -Encoding utf8
