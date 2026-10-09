package com.pluchon.meng.verification;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FrameworkProbeTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrameworkProbeTest {
    @SpringBootApplication
    static class Application {}

    @Autowired Environment environment;
    @Autowired NacosServiceManager serviceManager;
    @Autowired NacosDiscoveryProperties discoveryProperties;

    @Test
    void nacosConfigurationPrecedenceAndRealRegistration() throws Exception {
        assertEquals("service", environment.getProperty("verification.layer"));
        assertEquals(System.getenv("MENG_NACOS_NAMESPACE"), environment.getProperty("MENG_NACOS_NAMESPACE"));
        assertEquals(System.getenv("MENG_NACOS_NAMESPACE"), discoveryProperties.getNamespace());
        var naming = serviceManager.getNamingService(discoveryProperties.getNacosProperties());
        var instances = naming.getAllInstances(environment.getProperty("spring.application.name"), "MENG_GROUP");
        assertFalse(instances.isEmpty());
        assertTrue(instances.stream().anyMatch(instance -> instance.getPort() == Integer.parseInt(environment.getProperty("local.server.port"))));
    }
}
