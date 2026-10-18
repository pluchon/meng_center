$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
$composeArguments = Get-MengComposeArguments
$container = & docker @composeArguments ps --quiet mysql
if (-not $container) { throw 'Start the project MySQL container first.' }
# Passwords go through stdin, never through command arguments or output.
$prior = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:MYSQL_ROOT_PASSWORD
    foreach ($domain in @('content','growth','im','companion')) {
        $name = 'MENG_MYSQL_' + $domain.ToUpperInvariant() + '_PASSWORD'
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not $value) { throw "Missing $name" }
        $hex = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($value))
        $sql = @"
CREATE DATABASE IF NOT EXISTS meng_${domain} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET @password = CONVERT(0x${hex} USING utf8mb4);
SET @command = CONCAT('CREATE USER IF NOT EXISTS ''meng_${domain}_app''@''%'' IDENTIFIED BY ', QUOTE(@password));
PREPARE command FROM @command;
EXECUTE command;
DEALLOCATE PREPARE command;
GRANT ALL PRIVILEGES ON meng_${domain}.* TO 'meng_${domain}_app'@'%';
"@
        $sql | & docker exec -i -e MYSQL_PWD $container mysql -uroot
        if ($LASTEXITCODE -ne 0) { throw "Database initialization failed: $domain" }
        Write-Output "meng_${domain} initialized; existing credentials preserved."
    }
} finally { $env:MYSQL_PWD = $prior; $sql = $null; $value = $null; $hex = $null }
