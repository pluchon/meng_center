$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Nacos.ps1"
Import-MengEnvironment

try {
    $headers = Get-MengNacosHeaders
} catch {
    # First installation only. An existing administrator password is never reset.
    $null = Invoke-MengNacosRequest 'Post' '/v3/auth/user/admin' @{ password = $env:MENG_NACOS_PASSWORD }
    $headers = Get-MengNacosHeaders
}
$namespaces = Invoke-MengNacosRequest 'Get' '/v3/console/core/namespace/list' @{} $headers
if ($namespaces.code -ne 0) { throw 'Cannot list Nacos namespaces.' }
if (-not ($namespaces.data | Where-Object namespace -EQ $env:MENG_NACOS_NAMESPACE)) {
    $created = Invoke-MengNacosRequest 'Post' '/v3/console/core/namespace' @{
        customNamespaceId = $env:MENG_NACOS_NAMESPACE
        namespaceName = $env:MENG_NACOS_NAMESPACE
        namespaceDesc = 'Meng local development'
    } $headers
    if ($created.code -ne 0 -or -not $created.data) { throw 'Nacos namespace creation failed.' }
}
Write-Output 'Nacos administrator login and development namespace verified.'
