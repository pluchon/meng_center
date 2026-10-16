$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$names = @('MYSQL_ROOT_PASSWORD','MAIL_PASSWORD','ALIYUN_ACCESS_KEY_ID','ALIYUN_ACCESS_KEY_SECRET','DASHSCOPE_API_KEY','MENG_JWT_SECRET','MENG_PII_ENC_KEY','MENG_PII_HASH_KEY','MENG_MYSQL_USER_PASSWORD','MENG_MYSQL_CONTENT_PASSWORD','MENG_MYSQL_GROWTH_PASSWORD','MENG_MYSQL_IM_PASSWORD','MENG_REDIS_PASSWORD','MENG_ES_ELASTIC_PASSWORD','MENG_ES_CONTENT_PASSWORD','MENG_MYSQL_XXLJOB_PASSWORD','MENG_XXL_JOB_ACCESS_TOKEN','MENG_XXL_JOB_ADMIN_PASSWORD','MENG_RABBITMQ_PASSWORD','MENG_NACOS_PASSWORD','MENG_NACOS_AUTH_TOKEN','MENG_NACOS_AUTH_IDENTITY_VALUE','MENG_ADMIN_PASSWORD')
$values = @{}
foreach ($name in $names) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { $values[$name] = $value }
}
$files = & rg --files --hidden --no-ignore -g '!.git/**' -g '!**/node_modules/**' -g '!**/dist/**' -g '!**/.venv/**' -g '*.java' -g '*.xml' -g '*.yml' -g '*.yaml' -g '*.json' -g '*.jsonl' -g '*.py' -g '*.toml' -g '*.lock' -g '*.js' -g '*.mjs' -g '*.vue' -g '*.scss' -g '*.md' -g '*.ps1' -g '*.sh' -g '*.properties' -g '*.log' $root
$matches = @()
foreach ($file in $files) {
    $stream = [IO.FileStream]::new($file, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
    foreach ($name in $values.Keys) {
        if ($content.Contains($values[$name])) { $matches += @{ file = [IO.Path]::GetRelativePath($root, $file); variable = $name } }
    }
}
$report = @{ checkedAt = [DateTimeOffset]::Now.ToString('o'); passed = $matches.Count -eq 0; checkedFiles = $files.Count; matches = $matches }
$report | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8NoBOM (Join-Path $PSScriptRoot 'test-output/secrets-report.json')
if ($matches.Count) { throw 'Credential literals were found. Inspect the report; values are not displayed.' }
Write-Output "Credential scan passed across $($files.Count) text files."
