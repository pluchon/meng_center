from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class TaskOptions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    temperature: float | None = Field(default=None, ge=0, le=2)
    timeout: float | None = Field(default=None, gt=0, le=60)
    retries: int | None = Field(default=None, ge=0, le=1)
    fallback: Literal["unavailable", "suspect"] | None = None


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MENG_AI_", extra="ignore")

    api_key: SecretStr = Field(validation_alias="DASHSCOPE_API_KEY")
    deepseek_api_key: SecretStr = Field(default=SecretStr(""), validation_alias="DEEPSEEK_API_KEY")
    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    agent_model: str = "qwen3.8-flash"
    speech_model: str = "deepseek-flash"
    judge_model: str = "qwen3.8-flash"
    review_model: str = "qwen3.8-max"
    writer_model: str = "qwen3.8-max"
    assist_model: str = "qwen3.8-flash"
    memory_model: str = "qwen3.8-flash"
    model_overrides: dict[str, dict] = Field(default_factory=dict)
    task_overrides: dict[str, TaskOptions] = Field(default_factory=dict)
    tool_cache_seconds: dict[str, Annotated[int, Field(ge=0, le=3600)]] = Field(default_factory=dict)
    rerank_model: str = "qwen3.7-text-rerank"
    rerank_url: str = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    embedding_url: str = (
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
    )
    rerank_thresholds: dict[str, float] = {"search": 0.45, "related": 0.43, "recall": 0.5}
    rerank_instructions: dict[str, str] = Field(default_factory=dict)
    requests_per_minute: int = Field(default=1200, ge=1)
    tokens_per_minute: int = Field(default=1_000_000, ge=1)
    model_queue_seconds: float = Field(default=2, gt=0, le=10)
    circuit_seconds: int = Field(default=30, ge=1)
    input_price_per_million: float = Field(default=0, ge=0)
    output_price_per_million: float = Field(default=0, ge=0)
    cached_price_ratio: float = Field(default=0.2, ge=0, le=1)
    vision_model: str = "qwen3.8-flash"
    embedding_model: str = "qwen3.7-text-embedding-flash"
    embedding_dimensions: int = Field(default=1024, ge=1024, le=1024)
    cover_model: str = "qwen-image-3.0-pro"
    cover_url: str = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    structured_method: str = "function_calling"
    concurrency: int = Field(default=4, ge=1, le=32)
    timezone: str = "Asia/Taipei"
    redis_host: str = "127.0.0.1"
    redis_port: int = Field(default=16379, validation_alias="MENG_REDIS_PORT")
    redis_password: SecretStr = Field(validation_alias="MENG_REDIS_PASSWORD")
    rabbit_host: str = "127.0.0.1"
    rabbit_port: int = Field(default=25672, validation_alias="MENG_RABBITMQ_PORT")
    rabbit_username: str = Field(validation_alias="MENG_RABBITMQ_USERNAME")
    rabbit_password: SecretStr = Field(validation_alias="MENG_RABBITMQ_PASSWORD")
    nacos_addr: str = Field(validation_alias="MENG_NACOS_ADDR")
    nacos_namespace: str = Field(validation_alias="MENG_NACOS_NAMESPACE")
    nacos_username: str = Field(validation_alias="MENG_NACOS_USERNAME")
    nacos_password: SecretStr = Field(validation_alias="MENG_NACOS_PASSWORD")
    discovery_enabled: bool = True
