package com.pluchon.meng.im.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 配置导入设成必选还不够：Nacos 不可用时客户端会拿本地快照顶上，这里拒绝用快照启动
@Component
public final class NacosStartupGuard implements ApplicationRunner {

    // 配置中心就绪检查路径
    private static final String AVAILABLE = "UP";

    // 配置中心连接管理组件
    @Autowired
    private NacosConfigManager manager;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!AVAILABLE.equals(manager.getConfigService().getServerStatus())) {
            throw new IllegalStateException(
                "Nacos is unavailable; cached configuration cannot authorize startup."
            );
        }
    }
}
