"""评测专用的固定时钟与工具录制结果，不访问网络和业务服务。"""

from datetime import date, timedelta

from app.runtime.tools.registry import TOOLS


def scene(index, clock=None):
    day = date(2026, 10, 24) + timedelta(days=index)
    clock = clock or {0: "早上八点", 10: "晚上七点", 15: "晚上十点", 20: "凌晨两点"}.get(
        index % 30, "下午三点"
    )
    return (
        f"{day.year}年{day.month}月{day.day}日，星期{'一二三四五六日'[day.weekday()]}，用户当地时间{clock}。"
    )


class RecordedTools:
    async def execute(self, call, context):
        name = call["name"]
        TOOLS[name].schema.model_validate(call["args"])
        facts = {
            "search_posts": ["公开帖子《Vue 组件入门》介绍组件拆分和属性传递。"],
            "read_post": ["公开帖子《Vue 组件入门》的正文介绍组件拆分和属性传递。"],
            "my_recent_posts": ["测试用户最近公开发布了一篇学习 Vue 的帖子。"],
            "my_growth": ["测试用户最近有签到和发帖记录。"],
            "web_search": ["录制的天气资料：今天台北晴，气温二十六度。"],
            "read_link": ["录制网页介绍了 Vue 的组件与属性传递。"],
            "search_images": ["找到一张蓝天白云的风景图片，图片会随回复展示。"],
            "draw_picture": ["已登记后台画图，完成后另行通知，目前没有成品。"],
            "make_toy": ["小玩意已经制作完成，可以点击运行。"],
            "recall": [],
        }
        if name == "recall":
            statements = [turn.content for turn in context["request"].history if turn.role == "USER"]
            if "我搬到台北了。" in statements:
                facts[name] = ["用户先前住在台中，后来明确说已搬到台北。"]
            elif "我住在台中，下周要考试。" in statements:
                facts[name] = ["用户说过住在台中，下周要考试。"]
        if name == "search_images":
            context["images"].append(dict(url="https://example.org/recorded-sky.png", caption="蓝天白云"))
        elif name == "make_toy":
            context["works"].append(dict(kind="TOY", title="计数器", body="<button>计数</button>"))
        elif name == "draw_picture":
            context["drawings"].append(call["args"])
        context["toolUsage"].append(dict(name=name, success=True, recorded=True, elapsedMs=0))
        return dict(
            id=call["id"],
            success=True,
            facts=facts[name],
            speechFacts=facts[name] if name == "recall" else [],
            sources=[],
        )
