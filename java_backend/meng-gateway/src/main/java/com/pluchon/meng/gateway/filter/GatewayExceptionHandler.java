package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@Order(-2)
public final class GatewayExceptionHandler implements WebExceptionHandler {

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) return Mono.error(failure);
        CommonErrorCode code =
            failure instanceof ResponseStatusException response &&
            response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                ? CommonErrorCode.NOT_FOUND
                : CommonErrorCode.INTERNAL_ERROR;
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatusCode.valueOf(code.httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = json.writeValueAsBytes(ApiResponse.failure(code, null));
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
