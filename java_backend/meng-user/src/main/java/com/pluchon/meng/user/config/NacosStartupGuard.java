package com.pluchon.meng.user.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Non-optional imports alone can still use the SDK's local snapshot during an outage. */
@Component
public final class NacosStartupGuard implements ApplicationRunner {

    private static final String AVAILABLE = "UP";
    private final NacosConfigManager manager;

    public NacosStartupGuard(NacosConfigManager manager) {
        this.manager = manager;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!AVAILABLE.equals(manager.getConfigService().getServerStatus())) throw new IllegalStateException(
            "Nacos is unavailable; cached configuration cannot authorize startup."
        );
    }
}
