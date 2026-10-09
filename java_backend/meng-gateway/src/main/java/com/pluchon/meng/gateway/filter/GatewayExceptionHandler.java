package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

// 网关层的兜底异常处理：没有路由返回 404，其余一律 500，不把内部信息带给客户端
@Component
@Order(-2)
public final class GatewayExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        boolean notFound =
            failure instanceof ResponseStatusException response &&
            response.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
        return GatewayResponses.failure(
            exchange,
            notFound ? CommonErrorCode.NOT_FOUND : CommonErrorCode.INTERNAL_ERROR
        );
    }
}
