Set-StrictMode -Version Latest

function Import-MengEnvironment {
    # The Codex process may predate a change in user environment variables.
    $userVariables = [Environment]::GetEnvironmentVariables('User')
    foreach ($name in $userVariables.Keys) {
        if ($name -like 'MENG_*' -or $name -in @('MYSQL_ROOT_PASSWORD', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'ALIYUN_ACCESS_KEY_ID', 'ALIYUN_ACCESS_KEY_SECRET', 'DASHSCOPE_API_KEY', 'DEEPSEEK_API_KEY')) {
            [Environment]::SetEnvironmentVariable($name, $userVariables[$name], 'Process')
        }
    }
}

function Get-MengComposeArguments {
    $deployDirectory = Split-Path $PSScriptRoot -Parent
    return @('compose', '--project-name', 'meng_center', '--env-file', (Join-Path $deployDirectory 'ports.env'), '-f', (Join-Path $deployDirectory 'compose.yml'))
}

function Get-MengPorts {
    $ports = @{}
    $deployDirectory = Split-Path $PSScriptRoot -Parent
    foreach ($line in Get-Content (Join-Path $deployDirectory 'ports.env')) {
        if ($line -match '^(MENG_\w+_PORT)=(\d+)$') {
            $ports[$Matches[1]] = [int] $Matches[2]
        }
    }
    return $ports
}
