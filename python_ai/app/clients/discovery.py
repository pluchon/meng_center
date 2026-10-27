from __future__ import annotations

import json

from v2.nacos import (
    ClientConfigBuilder,
    ConfigParam,
    DeregisterInstanceParam,
    ListInstanceParam,
    NacosConfigService,
    NacosNamingService,
    RegisterInstanceParam,
)

from app.config.settings import Settings


class Discovery:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.client: NacosNamingService | None = None
        self.config_client = None

    async def start(self) -> None:
        if not self.settings.discovery_enabled:
            return
        settings = self.settings
        config = (
            ClientConfigBuilder()
            .server_address(settings.nacos_addr)
            .namespace_id(settings.nacos_namespace)
            .username(settings.nacos_username)
            .password(settings.nacos_password.get_secret_value())
            .build()
        )
        self.client = await NacosNamingService.create_naming_service(config)
        self.config_client = await NacosConfigService.create_config_service(config)
        raw = await self.config_client.get_config(ConfigParam(data_id="meng-ai.yml", group="MENG_GROUP"))
        if raw:
            values = json.loads(raw)
            allowed = {
                "model_overrides",
                "rerank_thresholds",
                "rerank_instructions",
                "requests_per_minute",
                "tokens_per_minute",
                "model_queue_seconds",
                "circuit_seconds",
            } | {name for name in Settings.model_fields if name.endswith("_model")}
            if not isinstance(values, dict) or set(values) - allowed:
                raise ValueError("AI 配置包含未知或不允许的字段")
            from pydantic import TypeAdapter

            for key, value in values.items():
                value = TypeAdapter(Settings.model_fields[key].rebuild_annotation()).validate_python(value)
                setattr(self.settings, key, value)
        if not await self.client.register_instance(
            RegisterInstanceParam(
                service_name="meng-ai",
                group_name="MENG_GROUP",
                ip="127.0.0.1",
                port=8106,
                ephemeral=True,
                enabled=True,
                healthy=True,
            )
        ):
            raise RuntimeError("Nacos registration failed")

    async def address(self, service):
        if self.client is None:
            raise RuntimeError("服务发现未启用")
        instances = await self.client.list_instances(
            ListInstanceParam(service_name=service, group_name="MENG_GROUP", healthy_only=True)
        )
        if not instances:
            raise RuntimeError("内部服务不可用")
        instance = instances[0]
        return f"http://{instance.ip}:{instance.port}"

    async def close(self) -> None:
        if self.config_client:
            await self.config_client.shutdown()
        if self.client:
            try:
                await self.client.deregister_instance(
                    DeregisterInstanceParam(
                        service_name="meng-ai",
                        group_name="MENG_GROUP",
                        ip="127.0.0.1",
                        port=8106,
                    )
                )
            finally:
                await self.client.shutdown()
