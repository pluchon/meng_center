param([string] $MavenPath)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../deploy/scripts/Nacos.ps1"
Import-MengEnvironment
if (-not $MavenPath) {
    $ideaDirectory = Get-ChildItem 'C:/Program Files/JetBrains' -Directory -Filter 'IntelliJ IDEA *' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $ideaDirectory) { throw 'Set -MavenPath to the IDEA bundled Maven command.' }
    $MavenPath = Join-Path $ideaDirectory.FullName 'plugins/maven-plugin/lib/maven3/bin/mvn.cmd'
}
if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'IDEA Maven command not found.' }
$outputDirectory = Join-Path $PSScriptRoot 'test-output'
$null = New-Item -ItemType Directory -Force -Path $outputDirectory
$headers = Get-MengNacosHeaders
$composeArguments = Get-MengComposeArguments
$ports = Get-MengPorts
$env:MENG_PROBE_MYSQL_URL = "jdbc:mysql://127.0.0.1:$($ports.MENG_MYSQL_PORT)/meng_user?characterEncoding=utf8&connectionTimeZone=UTC"
$report = [ordered]@{ checkedAt = [DateTimeOffset]::Now.ToString('o'); passed = $false; probes = @(); cleanupPassed = $false }
$configurations = @(
    @{ id = 'verification-common.yml'; content = "verification:`n  layer: common`n" },
    @{ id = 'verification-framework.yml'; content = "verification:`n  layer: service`nMENG_NACOS_NAMESPACE: ignored-nacos-value`n" }
)
$published = @()
try {
    foreach ($configuration in $configurations) {
        $result = Invoke-MengNacosRequest 'Post' '/v3/console/cs/config' @{
            dataId = $configuration.id; groupName = 'MENG_GROUP'; namespaceId = $env:MENG_NACOS_NAMESPACE
            type = 'yaml'; content = $configuration.content
        } $headers
        if ($result.code -ne 0 -or -not $result.data) { throw 'Probe configuration publication failed.' }
        $published += $configuration.id
    }
    foreach ($probe in @('framework', 'persistence', 'captcha')) {
        $pom = Join-Path $PSScriptRoot "probes/$probe/pom.xml"
        & $MavenPath -q -ntp -f $pom test *> (Join-Path $outputDirectory "$probe.log")
        $passed = $LASTEXITCODE -eq 0
        $report.probes += [ordered]@{ name = $probe; passed = $passed }
        Write-Output "$probe : $passed"
        if (-not $passed) { throw "Compatibility probe failed: $probe. Inspect verification/test-output/$probe.log locally." }
    }
    $report.passed = $true
} finally {
    $cleanupErrors = @()
    foreach ($dataId in $published) {
        try {
        $result = Invoke-MengNacosRequest 'Delete' '/v3/console/cs/config' @{
            dataId = $dataId; groupName = 'MENG_GROUP'; namespaceId = $env:MENG_NACOS_NAMESPACE
        } $headers
        if ($result.code -ne 0 -or -not $result.data) { throw 'Probe configuration cleanup failed.' }
        } catch { $cleanupErrors += 'Probe configuration cleanup failed.' }
    }
    # Remove only the two named tables owned by these probes; user tables remain untouched.
    & docker @composeArguments exec -T mysql sh -c 'MYSQL_PWD="$MENG_MYSQL_USER_PASSWORD" mysql -h 127.0.0.1 -u meng_user_app meng_user -e "DROP TABLE IF EXISTS verification_persistence_probe; DROP TABLE IF EXISTS verification_flyway_history;"' 2>$null
    if ($LASTEXITCODE -ne 0) { $cleanupErrors += 'Probe database cleanup failed.' }
    $report.cleanupPassed = $cleanupErrors.Count -eq 0
    $report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outputDirectory 'compatibility.json') -Encoding utf8
    Remove-Item Env:MENG_PROBE_MYSQL_URL
    if ($cleanupErrors.Count -gt 0) { throw ($cleanupErrors -join ' ') }
}
