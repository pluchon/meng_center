param([ValidateSet('meng-user', 'meng-gateway', 'meng-content', 'meng-growth', 'meng-im', 'meng-companion')] [string] $Service, [switch] $InitializeAdmin, [string[]] $ConfigurationArguments = @())
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
$ports = Get-MengPorts
foreach ($name in $ports.Keys) { [Environment]::SetEnvironmentVariable($name, [string] $ports[$name], 'Process') }
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jar = Join-Path $repository "java_backend/$Service/target/$Service-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend before starting.' }
$maxHeap = 384
if ($env:MENG_JAVA_MAX_HEAP_MB -and (-not [int]::TryParse($env:MENG_JAVA_MAX_HEAP_MB, [ref]$maxHeap) -or $maxHeap -lt 128 -or $maxHeap -gt 4096)) {
    throw 'MENG_JAVA_MAX_HEAP_MB must be between 128 and 4096.'
}
$arguments = @('-Xms64m', "-Xmx${maxHeap}m", '-XX:MaxMetaspaceSize=256m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-jar', $jar)
if ($InitializeAdmin) { $arguments += '--meng.initialize-admin=true' }
$arguments += $ConfigurationArguments
& java @arguments
if ($LASTEXITCODE -ne 0) { throw "$Service exited with an error." }
