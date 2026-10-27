from app.main import app
from app.runtime.tools.registry import TOOLS


def test_read_only_tools_and_internal_contract_are_closed():
    assert set(TOOLS) == {"recall", "search_posts", "read_post", "my_recent_posts", "my_growth"}
    paths = {
        route.path.rsplit("/", 1)[-1]
        for route in app.routes
        if getattr(route, "path", "").startswith("/ai/internal/companion/")
    }
    assert paths == {"replies", "summaries", "consolidations", "embeddings"}
