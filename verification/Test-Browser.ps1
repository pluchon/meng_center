$ErrorActionPreference = 'Stop'
$reference = $null
$root = Split-Path $PSScriptRoot -Parent
try {
    if (-not (Get-NetTCPConnection -LocalPort 5180 -State Listen -ErrorAction SilentlyContinue)) {
        $reference = Start-Process -FilePath (Get-Command python).Source -ArgumentList @('-m', 'http.server', '5180', '--bind', '127.0.0.1', '--directory', (Join-Path $root 'vue_front/front_user/pages_design')) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'test-output/reference.log') -RedirectStandardError (Join-Path $PSScriptRoot 'test-output/reference.err.log') -PassThru
    }
    & "$PSScriptRoot/Invoke-NodeCheck.ps1" -Check browser
} finally {
    if ($reference -and -not $reference.HasExited) { Stop-Process -Id $reference.Id }
}
