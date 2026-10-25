import re
from html.parser import HTMLParser

CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:"


class Inspector(HTMLParser):
    def handle_starttag(self, tag, attrs):
        if tag in {"form", "iframe", "object", "embed", "base"}:
            raise ValueError("小玩意包含不允许的元素")
        for key, value in attrs:
            if key == "srcdoc" or (tag == "input" and key == "type" and value.lower() == "password"):
                raise ValueError("小玩意包含不允许的输入")
            if key in {"src", "href", "action", "formaction", "poster", "srcset"} and value:
                if not (key == "src" and value.startswith("data:image/")):
                    raise ValueError("小玩意引用外部资源")
            if tag == "meta" and key == "http-equiv":
                raise ValueError("小玩意不能修改浏览器策略")


def secure(value):
    value = re.sub(r"^```(?:html)?\s*|\s*```$", "", value.strip(), flags=re.I)
    if len(value.encode("utf-8")) > 20_000:
        raise ValueError("小玩意超过20KB")
    if re.search(
        r"\b(?:fetch|XMLHttpRequest|WebSocket|import|EventSource|sendBeacon|location)\b", value, re.I
    ):
        raise ValueError("小玩意包含外部访问")
    Inspector().feed(value)
    policy = '<meta http-equiv="Content-Security-Policy" content="' + CSP + '">'
    if re.search(r"<head(?:\s[^>]*)?>", value, re.I):
        value = re.sub(r"(<head(?:\s[^>]*)?>)", lambda m: m[0] + policy, value, count=1, flags=re.I)
    else:
        value = "<!doctype html><head>" + policy + "</head>" + value
    if len(value.encode("utf-8")) > 20_000:
        raise ValueError("含策略的小玩意超过20KB")
    return value
