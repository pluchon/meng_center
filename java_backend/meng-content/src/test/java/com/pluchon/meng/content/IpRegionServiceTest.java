package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pluchon.meng.common.web.region.IpRegionService;
import org.junit.jupiter.api.Test;

class IpRegionServiceTest {

    @Test
    void privateAddressesAreHiddenAndPublicCountryResolved() throws Exception {
        try (var region = new AutoCloseableRegion()) {
            assertNull(region.service.resolve("127.0.0.1"));
            assertNull(region.service.resolve("::1"));
            assertNull(region.service.resolve("192.168.1.1"));
            assertNull(region.service.resolve("host.invalid"));
            assertEquals("美国", region.service.resolve("8.8.8.8"));
            assertEquals("浙江", region.service.resolve("223.5.5.5"));
            assertEquals("北京", region.service.resolve("119.29.29.29"));
            assertEquals("江苏", region.service.resolve("114.114.114.114"));
        }
    }

    private static class AutoCloseableRegion implements AutoCloseable {

        final IpRegionService service = new IpRegionService();

        AutoCloseableRegion() throws Exception {}

        public void close() throws Exception {
            service.close();
        }
    }
}
