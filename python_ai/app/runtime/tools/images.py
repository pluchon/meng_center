import base64
import hashlib
import warnings
from io import BytesIO

from PIL import Image

from app.runtime.safety.policy import POLICY
from app.runtime.tools.netguard import download, normalize_url
from app.runtime.tools.web import private_query
from app.schemas.models import Schema

Image.MAX_IMAGE_PIXELS = 20_000_000


class ImageReview(Schema):
    safe: bool
    matches: bool
    caption: str


def transcode(data):
    with warnings.catch_warnings():
        warnings.simplefilter("error", Image.DecompressionBombWarning)
        with Image.open(BytesIO(data)) as source:
            if min(source.size) < 200:
                raise ValueError("图片尺寸过小")
            source.seek(0)
            image = source.convert("RGB")
            image.thumbnail((1280, 1280))
            pixels = list(image.resize((8, 8)).convert("L").getdata())
            average = sum(pixels) / 64
            fingerprint = sum((1 << i) for i, pixel in enumerate(pixels) if pixel >= average)
            output = BytesIO()
            image.save(output, format="WEBP", quality=80)
            return output.getvalue(), fingerprint


async def search(service, request, query):
    await private_query(service, request, query)
    payload = await service.tavily.call(
        "search",
        dict(
            query=query,
            include_images=True,
            include_image_descriptions=True,
            safe_search=True,
            search_depth="basic",
            max_results=8,
        ),
    )
    rows, urls = [], set()
    for row in payload.get("images", []):
        if not isinstance(row, dict):
            continue
        try:
            url = normalize_url(row["url"], https_only=True)
        except (ValueError, KeyError):
            continue
        if url not in urls:
            urls.add(url)
            rows.append({**row, "url": url})
    rank = await service.models.rerank.rank(query, [r.get("description", "") for r in rows[:20]], 4, "web")
    result, fingerprints = [], []
    for index in rank["indexes"]:
        row = rows[index]
        try:
            data, fingerprint = transcode(await download(service.http, row["url"]))
            if any((fingerprint ^ old).bit_count() <= 4 for old in fingerprints):
                continue
            key = "meng:ai:web-image:" + hashlib.sha256((row["url"] + "\n" + query).encode()).hexdigest()
            cached = await service.models.redis.get(key)
            if cached:
                review = ImageReview.model_validate_json(cached)
            else:
                thumb = Image.open(BytesIO(data))
                thumb.thumbnail((512, 512))
                output = BytesIO()
                thumb.save(output, "WEBP")
                review = await service.structured(
                    ImageReview,
                    POLICY + "\n审核图片的安全、是否匹配查询，并写一句图片说明。",
                    [
                        {"type": "text", "text": query},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": "data:image/webp;base64,"
                                + base64.b64encode(output.getvalue()).decode()
                            },
                        },
                    ],
                    purpose="vision",
                    multimodal=True,
                )
                if review is None:
                    continue
                await service.models.redis.set(key, review.model_dump_json(), ex=604800)
            if review.safe and review.matches:
                result.append(
                    dict(
                        data=base64.b64encode(data).decode(),
                        sourceUrl=row["url"],
                        pageUrl=normalize_url(row.get("page_url", row["url"])),
                        title=row.get("description", "图片")[:150],
                        caption=review.caption[:200],
                    )
                )
                fingerprints.append(fingerprint)
            if len(result) >= 2:
                break
        except Exception:
            continue
    return result
