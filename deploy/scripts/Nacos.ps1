. "$PSScriptRoot/Environment.ps1"

function Invoke-MengNacosRequest([string] $Method, [string] $Path, [hashtable] $Body = @{}, [hashtable] $Headers = @{}) {
    $ports = Get-MengPorts
    $uri = "http://127.0.0.1:$($ports.MENG_NACOS_CONSOLE_PORT)$Path"
    $parameters = @{ Method = $Method; Uri = $uri; Headers = $Headers; TimeoutSec = 15 }
    if ($Body.Count -gt 0 -and $Method -in @('Get', 'Delete')) {
        $query = ($Body.GetEnumerator() | ForEach-Object {
            '{0}={1}' -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString([string] $_.Value)
        }) -join '&'
        $parameters.Uri = "${uri}?$query"
    } elseif ($Body.Count -gt 0) {
        $parameters.Body = $Body
        $parameters.ContentType = 'application/x-www-form-urlencoded'
    }
    try {
        return Invoke-RestMethod @parameters
    } catch {
        # Error response bodies may contain credentials or tokens; never forward them.
        throw "Nacos $Method $Path failed."
    }
}

function Get-MengNacosHeaders {
    $login = Invoke-MengNacosRequest 'Post' '/v3/auth/user/login' @{
        username = $env:MENG_NACOS_USERNAME
        password = $env:MENG_NACOS_PASSWORD
    }
    if (-not $login.accessToken) { throw 'Nacos authentication did not return a token.' }
    return @{ accessToken = $login.accessToken }
}
