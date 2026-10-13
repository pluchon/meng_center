package com.pluchon.meng.common.web.region;

import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// 离线解析请求地址的属地
@Service
@org.springframework.context.annotation.Lazy
public class IpRegionService {

    // 离线属地查询器
    private final Ip2Region searcher;

    public IpRegionService() throws Exception {
        searcher = Ip2Region.create(
            Config.custom()
                .setCachePolicy(Config.BufferCache)
                .setXdbInputStream(
                    new ClassPathResource(
                        "ip/ip2region_v4.xdb"
                    ).getInputStream()
                )
                .asV4(),
            Config.custom()
                .setCachePolicy(Config.BufferCache)
                .setXdbInputStream(
                    new ClassPathResource(
                        "ip/ip2region_v6.xdb"
                    ).getInputStream()
                )
                .asV6()
        );
    }

    // 离线解析合法地址的属地
    public String resolve(String ip) {
        if (ip == null || !ip.matches("[0-9a-fA-F:.]+")) return null;
        try {
            var address = java.net.InetAddress.getByName(ip);
            if (
                address.isLoopbackAddress() ||
                address.isAnyLocalAddress() ||
                address.isSiteLocalAddress() ||
                address.isLinkLocalAddress() ||
                address.isMulticastAddress()
            ) return null;
            String region = searcher.search(ip);
            if (region == null || region.isBlank()) return null;
            String[] fields = region.split("\\|", -1);
            String country = fields[0];
            if (
                country.isBlank() ||
                country.equals("0") ||
                country.equals("Reserved") ||
                country.equals("内网IP")
            ) return null;
            String iso = fields.length > 4 ? fields[4] : "";
            if (iso.equals("TW")) return "台湾";
            if (iso.equals("HK")) return "香港";
            if (iso.equals("MO")) return "澳门";
            return iso.matches("[A-Z]{2}")
                ? new java.util.Locale("", iso).getDisplayCountry(
                      java.util.Locale.SIMPLIFIED_CHINESE
                  )
                : country;
        } catch (Exception invalid) {
            return null;
        }
    }

    // 释放属地查询器资源
    @PreDestroy
    public void close() throws Exception {
        searcher.close();
    }
}
