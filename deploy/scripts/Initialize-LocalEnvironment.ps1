$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment

function New-MengSecret {
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes)
}

function Set-MengVariableIfMissing([string] $Name, [scriptblock] $Factory) {
    if ([Environment]::GetEnvironmentVariable($Name, 'User') -or [Environment]::GetEnvironmentVariable($Name, 'Process')) {
        Write-Output "$Name already exists; preserved."
        return
    }
    $value = & $Factory
    [Environment]::SetEnvironmentVariable($Name, $value, 'User')
    [Environment]::SetEnvironmentVariable($Name, $value, 'Process')
    Write-Output "$Name initialized."
}

foreach ($name in @('MENG_JWT_SECRET', 'MENG_PII_ENC_KEY', 'MENG_PII_HASH_KEY', 'MENG_MYSQL_USER_PASSWORD', 'MENG_MYSQL_CONTENT_PASSWORD', 'MENG_MYSQL_GROWTH_PASSWORD', 'MENG_REDIS_PASSWORD', 'MENG_RABBITMQ_PASSWORD', 'MENG_NACOS_PASSWORD', 'MENG_NACOS_AUTH_TOKEN', 'MENG_NACOS_AUTH_IDENTITY_VALUE', 'MENG_ADMIN_PASSWORD')) {
    Set-MengVariableIfMissing $name { New-MengSecret }
}
Set-MengVariableIfMissing 'MENG_RABBITMQ_USERNAME' { 'meng_user_app' }
Set-MengVariableIfMissing 'MENG_ADMIN_USERNAME' { 'meng_admin' }
Set-MengVariableIfMissing 'MENG_NACOS_USERNAME' { 'nacos' }
Set-MengVariableIfMissing 'MENG_NACOS_NAMESPACE' { 'meng-dev' }
Set-MengVariableIfMissing 'MENG_NACOS_AUTH_IDENTITY_KEY' { 'meng-server-identity' }
$ports = Get-MengPorts
Set-MengVariableIfMissing 'MENG_NACOS_ADDR' { "127.0.0.1:$($ports.MENG_NACOS_PORT)" }

Set-MengVariableIfMissing 'MENG_CONTENT_IMAGE_ROOT' { Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'meng_center/content-images' }
