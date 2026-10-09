param([ValidateSet('meng-user', 'meng-gateway')] [string] $Service, [switch] $InitializeAdmin, [string[]] $ConfigurationArguments = @())
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
$ports = Get-MengPorts
foreach ($name in $ports.Keys) { [Environment]::SetEnvironmentVariable($name, [string] $ports[$name], 'Process') }
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jar = Join-Path $repository "java_backend/$Service/target/$Service-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend before starting.' }
$arguments = @('-Dfile.encoding=UTF-8', '-jar', $jar)
if ($InitializeAdmin) { $arguments += '--meng.initialize-admin=true' }
$arguments += $ConfigurationArguments
& java @arguments
if ($LASTEXITCODE -ne 0) { throw "$Service exited with an error." }
