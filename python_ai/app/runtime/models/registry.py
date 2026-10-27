from dataclasses import dataclass, replace


@dataclass(frozen=True)
class ModelSpec:
    model: str
    temperature: float = 0
    timeout: float = 10
    max_tokens: int = 1600
    tools: bool = False
    vision: bool = False
    thinking: bool = False
    n: int = 1
    context_tokens: int = 600_000
    fallbacks: tuple[str, ...] = ()


def registry(settings):
    flash = "qwen3.8-flash"
    specs = {
        "agent": ModelSpec(settings.agent_model, 0.2, tools=True),
        "speech": ModelSpec(settings.speech_model, 1, 10, 2000, fallbacks=("deepseek-v4-pro", flash)),
        "judge": ModelSpec(settings.judge_model, vision=True),
        "review": ModelSpec(settings.review_model, 0, 12, vision=True),
        "writer": ModelSpec(settings.writer_model, 0.7, 15, 3000, fallbacks=(flash,)),
        "assist": ModelSpec(settings.assist_model),
        "memory": ModelSpec(settings.memory_model, 0.2, 15, 3000),
        "vision": ModelSpec(settings.vision_model, 0, 12, vision=True),
        "embedding": ModelSpec(settings.embedding_model, timeout=3),
        "rerank": ModelSpec(settings.rerank_model, timeout=3),
        "image": ModelSpec(settings.cover_model, timeout=52),
    }
    for purpose, overrides in settings.model_overrides.items():
        if purpose not in specs:
            raise ValueError("未知模型用途")
        specs[purpose] = replace(specs[purpose], **overrides)
    return specs
