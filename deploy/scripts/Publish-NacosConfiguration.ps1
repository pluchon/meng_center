$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Nacos.ps1"
Import-MengEnvironment
$headers = Get-MengNacosHeaders
$directory = Join-Path (Split-Path $PSScriptRoot -Parent) 'nacos'
foreach ($file in Get-ChildItem -LiteralPath $directory -Filter '*.yml') {
    $result = Invoke-MengNacosRequest 'Post' '/v3/console/cs/config' @{
        dataId = $file.Name; groupName = 'MENG_GROUP'; namespaceId = $env:MENG_NACOS_NAMESPACE
        type = 'yaml'; content = [IO.File]::ReadAllText($file.FullName)
    } $headers
    if ($result.code -ne 0 -or -not $result.data) { throw "Nacos publication failed: $($file.Name)" }
    Write-Output "$($file.Name) published."
}
