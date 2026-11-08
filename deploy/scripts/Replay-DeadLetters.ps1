param(
    [Parameter(Mandatory)] [string] $Queue,
    [ValidateRange(1, 100)] [int] $MaxDeaths = 5,
    [switch] $DryRun
)
# 把死信队列里的消息放回各自原来的队列（取 x-death 记录的原队列），只投给原队列，不经主题交换机，别的服务不会重复收到。
# 同一条消息在原队列已经死过 MaxDeaths 次的不再放回，留在死信队列等人工处理。处理前先修好让消息失败的原因。
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Environment.ps1"
Import-MengEnvironment
$ports = Get-MengPorts
$base = "http://127.0.0.1:$($ports.MENG_RABBITMQ_MANAGEMENT_PORT)/api"
$vhost = [uri]::EscapeDataString('meng')
$pair = "$($env:MENG_RABBITMQ_USERNAME):$($env:MENG_RABBITMQ_PASSWORD)"
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }

function Invoke-Management([string] $Method, [string] $Path, $Body) {
    $arguments = @{ Method = $Method; Uri = $base + $Path; Headers = $headers; ContentType = 'application/json' }
    if ($null -ne $Body) { $arguments.Body = ($Body | ConvertTo-Json -Depth 20 -Compress) }
    # Invoke-RestMethod 把 JSON 数组当成一个对象输出，经变量返回才会逐条展开
    $result = Invoke-RestMethod @arguments
    return $result
}

# 原队列名和它在原队列死过几次
function Get-Origin($message) {
    # 没有 headers 或 x-death 的消息不知道来自哪里，原样留下
    $headers = $message.properties.PSObject.Properties['headers']
    $death = if ($headers) { $headers.Value.PSObject.Properties['x-death'] }
    if (-not $death) { return $null }
    $deaths = @($death.Value)
    if ($deaths.Count -eq 0 -or -not $deaths[0].queue) { return $null }
    return [pscustomobject]@{ Queue = $deaths[0].queue; Count = [int] $deaths[0].count }
}

function Publish-ToQueue($message, [string] $Target) {
    $result = Invoke-Management 'Post' "/exchanges/$vhost/amq.default/publish" @{
        properties = $message.properties
        routing_key = $Target
        payload = $message.payload
        payload_encoding = $message.payload_encoding
    }
    if (-not $result.routed) { throw "Message was not routed to queue $Target." }
}

$escaped = [uri]::EscapeDataString($Queue)
# 队列统计有几秒延迟，按实际取到的消息计数：取出后立即放回原位
$peek = @(Invoke-Management 'Post' "/queues/$vhost/$escaped/get" @{ count = 10000; ackmode = 'reject_requeue_true'; encoding = 'base64' })
$pending = $peek.Count
if ($pending -eq 0) { Write-Output "$Queue is empty."; return }
if ($DryRun) {
    $peek | ForEach-Object { Get-Origin $_ } | Group-Object Queue |
        ForEach-Object { Write-Output ("{0}: {1} message(s), most deaths {2}" -f $_.Name, $_.Count, ($_.Group | Measure-Object Count -Maximum).Maximum) }
    return
}
$replayed = 0
$kept = 0
# 只处理开始时已有的条数，放回死信队列的消息不会被再取一遍
for ($i = 0; $i -lt $pending; $i++) {
    $message = @(Invoke-Management 'Post' "/queues/$vhost/$escaped/get" @{ count = 1; ackmode = 'ack_requeue_false'; encoding = 'base64' })[0]
    if ($null -eq $message) { break }
    $origin = Get-Origin $message
    if ($null -eq $origin -or $origin.Count -ge $MaxDeaths) {
        Publish-ToQueue $message $Queue
        $kept++
        continue
    }
    try {
        Publish-ToQueue $message $origin.Queue
        $replayed++
    } catch {
        # 原队列投不进去就放回死信队列，不丢消息
        Publish-ToQueue $message $Queue
        throw "Replay stopped: cannot publish to $($origin.Queue). The message was returned to $Queue."
    }
}
Write-Output "Replayed $replayed message(s); kept $kept in $Queue."
