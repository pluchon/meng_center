import json
from datetime import datetime
from zoneinfo import ZoneInfo


class Tavily:
    def __init__(self, settings, http, redis):
        self.settings, self.http, self.redis = settings, http, redis

    async def call(self, endpoint, payload):
        if not self.settings.tavily_api_key.get_secret_value():
            raise RuntimeError("联网服务未配置")
        date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
        key = "meng:ai:tavily:" + date
        allowed = await self.redis.eval(
            "local n=tonumber(redis.call('GET',KEYS[1]) or 0); if n>=tonumber(ARGV[1]) then return 0 end; "
            "redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],172800); return 1",
            1,
            key,
            self.settings.tavily_daily_credits,
        )
        if not allowed:
            raise RuntimeError("联网服务当日额度已用完")
        response = await self.http.post(
            "https://api.tavily.com/" + endpoint,
            headers={"Authorization": "Bearer " + self.settings.tavily_api_key.get_secret_value()},
            json=payload,
            timeout=5,
        )
        response.raise_for_status()
        data = response.json()
        if len(json.dumps(data)) > 2_000_000:
            raise ValueError("联网结果过大")
        return data
