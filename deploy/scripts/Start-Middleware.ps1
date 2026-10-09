$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
$composeArguments = Get-MengComposeArguments
& docker @composeArguments config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid or required variables are missing.' }

$ports = Get-MengPorts
if ($ports.MENG_NACOS_GRPC_PORT -ne $ports.MENG_NACOS_PORT + 1000) {
    throw 'Nacos host gRPC port must equal host main port + 1000.'
}
if (@($ports.Values | Sort-Object -Unique).Count -ne $ports.Count) {
    throw 'Host port mappings contain duplicates.'
}
$existing = @(& docker @composeArguments ps --all --quiet)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing project containers.' }
if ($existing.Count -eq 0) {
    foreach ($port in $ports.Values) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try { $listener.Start() }
        catch { throw "Host port $port is unavailable. Update deploy/ports.env before starting." }
        finally { $listener.Stop() }
    }
}
& docker @composeArguments up -d
if ($LASTEXITCODE -ne 0) { throw 'Middleware startup failed.' }
