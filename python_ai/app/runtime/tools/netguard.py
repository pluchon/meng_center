import asyncio
import ipaddress
import socket
from urllib.parse import parse_qsl, urlencode, urljoin, urlsplit, urlunsplit


def normalize_url(value, https_only=False):
    parts = urlsplit(value)
    if parts.scheme.lower() not in ({"https"} if https_only else {"http", "https"}):
        raise ValueError("链接协议不合法")
    if not parts.hostname or parts.username or parts.password or parts.port not in (None, 80, 443):
        raise ValueError("链接地址不合法")
    host = parts.hostname.encode("idna").decode().lower()
    if ":" in host:
        host = "[" + host + "]"
    if parts.port:
        host += ":" + str(parts.port)
    query = [
        (k, v)
        for k, v in parse_qsl(parts.query, keep_blank_values=True)
        if not k.lower().startswith("utm_") and k.lower() not in {"spm", "from", "share"}
    ]
    return urlunsplit((parts.scheme.lower(), host, parts.path or "/", urlencode(query), ""))


async def resolve(url):
    parts = urlsplit(normalize_url(url))
    rows = await asyncio.get_running_loop().getaddrinfo(
        parts.hostname, parts.port or (443 if parts.scheme == "https" else 80), type=socket.SOCK_STREAM
    )
    addresses = list(dict.fromkeys(row[4][0] for row in rows))
    if not addresses or any(not ipaddress.ip_address(ip).is_global for ip in addresses):
        raise ValueError("链接指向非公共地址")
    return addresses


async def download(http, url):
    async with asyncio.timeout(4):
        for _ in range(4):
            url = normalize_url(url, https_only=True)
            parts = urlsplit(url)
            addresses = await resolve(url)
            address = addresses[0]
            netloc = "[" + address + "]" if ":" in address else address
            if parts.port:
                netloc += ":" + str(parts.port)
            pinned = urlunsplit((parts.scheme, netloc, parts.path, parts.query, ""))
            # URL 使用已校验 IP；Host 与 TLS SNI 仍指向原主机，连接阶段不会再次解析域名。
            async with http.stream(
                "GET",
                pinned,
                headers={"Host": parts.netloc},
                extensions={"sni_hostname": parts.hostname},
                follow_redirects=False,
                timeout=4,
            ) as response:
                if response.status_code in {301, 302, 303, 307, 308}:
                    url = urljoin(url, response.headers["location"])
                    continue
                response.raise_for_status()
                if response.headers.get("content-type", "").split(";")[0].lower() not in {
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "image/gif",
                }:
                    raise ValueError("不是支持的图片类型")
                if int(response.headers.get("content-length", "0")) > 5_000_000:
                    raise ValueError("图片过大")
                data = bytearray()
                async for chunk in response.aiter_bytes():
                    data.extend(chunk)
                    if len(data) > 5_000_000:
                        raise ValueError("图片过大")
                return bytes(data)
        raise ValueError("重定向次数过多")
