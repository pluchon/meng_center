from __future__ import annotations

from v2.nacos import ClientConfigBuilder, DeregisterInstanceParam, NacosNamingService, RegisterInstanceParam

from app.config.settings import Settings


class Discovery:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.client: NacosNamingService | None = None

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

    async def close(self) -> None:
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
