package com.pluchon.meng.user.client;

import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;

// 国内和国际短信客户端共用的阿里云连接参数，访问密钥只从环境变量读
final class AliyunSmsSupport {

    static final Gson GSON = new Gson();

    private static final String ACCESS_KEY_ID_ENV = "ALIYUN_ACCESS_KEY_ID";
    private static final String ACCESS_KEY_SECRET_ENV = "ALIYUN_ACCESS_KEY_SECRET";
    private static final int TIMEOUT_MILLIS = 5000;

    private AliyunSmsSupport() {}

    // 指定接入点的连接配置，连接和读取各限时五秒
    static Config config(String endpoint) {
        return new Config()
            .setAccessKeyId(System.getenv(ACCESS_KEY_ID_ENV))
            .setAccessKeySecret(System.getenv(ACCESS_KEY_SECRET_ENV))
            .setEndpoint(endpoint)
            .setReadTimeout(TIMEOUT_MILLIS)
            .setConnectTimeout(TIMEOUT_MILLIS);
    }
}
