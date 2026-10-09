package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.response.ApiResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

// 网关自己拒绝请求时的统一响应，格式与业务服务的失败响应一致
final class GatewayResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GatewayResponses() {}

    // 按错误码写状态和响应体；响应已经开始输出时不再改写
    static Mono<Void> failure(ServerWebExchange exchange, CommonErrorCode code) {
        return Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();
            if (response.isCommitted()) {
                return Mono.empty();
            }
            response.setStatusCode(HttpStatusCode.valueOf(code.httpStatus()));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = JSON.writeValueAsBytes(ApiResponse.failure(code, null));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        });
    }
}
