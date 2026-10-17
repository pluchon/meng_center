from __future__ import annotations

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MENG_AI_", extra="ignore")

    api_key: SecretStr = Field(validation_alias="DASHSCOPE_API_KEY")
    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    classifier_model: str = "qwen3.8-flash"
    writer_model: str = "qwen3.8-flash"
    companion_model: str = "qwen3.8-flash"
    companion_temperature: float = Field(default=0.85, ge=0.8, le=0.9)
    vision_model: str = "qwen3.8-flash"
    embedding_model: str = "qwen3.7-text-embedding-flash"
    embedding_dimensions: int = Field(default=1024, ge=1024, le=1024)
    cover_model: str = "qwen-image-3.0"
    cover_url: str = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    structured_method: str = "function_calling"
    concurrency: int = Field(default=4, ge=1, le=32)
    daily_token_limit: int = Field(default=1_000_000, ge=1)
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
