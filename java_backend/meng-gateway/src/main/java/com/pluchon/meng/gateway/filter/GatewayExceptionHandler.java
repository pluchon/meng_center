package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.error.DependencyFailures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

// 网关层的兜底异常处理：没有路由返回 404，下游或 Redis 不可用返回 503，其余 500，不把内部信息带给客户端
@Component
@Order(-2)
public final class GatewayExceptionHandler implements WebExceptionHandler {

    // 兜底异常的日志
    private static final Logger LOG = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        int status = failure instanceof ResponseStatusException response ? response.getStatusCode().value() : 0;
        if (status == HttpStatus.NOT_FOUND.value()) {
            return GatewayResponses.failure(exchange, CommonErrorCode.NOT_FOUND);
        }
        // 找不到下游实例、下游超时、Redis 连不上都属于暂时不可用
        boolean unavailable =
            status == HttpStatus.SERVICE_UNAVAILABLE.value() ||
            status == HttpStatus.GATEWAY_TIMEOUT.value() ||
            DependencyFailures.unavailable(failure);
        LOG.error("Gateway request failed: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), failure);
        return GatewayResponses.failure(
            exchange,
            unavailable ? CommonErrorCode.UNAVAILABLE : CommonErrorCode.INTERNAL_ERROR
        );
    }
}
