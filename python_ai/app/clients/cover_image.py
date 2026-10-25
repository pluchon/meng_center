from __future__ import annotations

from urllib.parse import urlparse

import httpx

from app.config.settings import Settings
from app.schemas.models import CoverPlan


class CoverImageClient:
    def __init__(self, settings: Settings, http: httpx.AsyncClient, gateway=None) -> None:
        self.settings = settings
        self.http = http
        self.gateway = gateway

    async def generate(self, plan: CoverPlan) -> bytes:
        if self.gateway:
            return await self.gateway.invoke(
                self.settings.cover_model,
                "image",
                len(plan.prompt),
                lambda: self._generate(plan),
                lambda result: {"images": 1},
                retry=False,
            )
        return await self._generate(plan)

    async def _generate(self, plan: CoverPlan) -> bytes:
        response = await self.http.post(
            self.settings.cover_url,
            headers=self.gateway.headers(),
            json={
                "model": self.settings.cover_model,
                "input": {"messages": [{"role": "user", "content": [{"text": plan.prompt}]}]},
                "parameters": {
                    "size": "1664*928",
                    "n": 1,
                    "watermark": True,
                    "negative_prompt": plan.negative_prompt,
                    "prompt_extend": False,
                },
            },
            timeout=52,
        )
        response.raise_for_status()
        content = response.json()["output"]["choices"][0]["message"]["content"]
        url = next(item["image"] for item in content if "image" in item)
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".aliyuncs.com"):
            raise ValueError("Unexpected generated image origin")
        async with self.http.stream("GET", url, timeout=10) as image:
            image.raise_for_status()
            data = bytearray()
            async for chunk in image.aiter_bytes():
                data.extend(chunk)
                if len(data) > 10_000_000:
                    raise ValueError("Generated image too large")
        return bytes(data)
