package com.pluchon.meng.user.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 配置导入设成必选还不够：Nacos 不可用时客户端会拿本地快照顶上，这里拒绝用快照启动
@Component
public final class NacosStartupGuard implements ApplicationRunner {

    private static final String AVAILABLE = "UP";

    @Autowired
    private NacosConfigManager manager;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!AVAILABLE.equals(manager.getConfigService().getServerStatus())) {
            throw new IllegalStateException("Nacos is unavailable; cached configuration cannot authorize startup.");
        }
    }
}
