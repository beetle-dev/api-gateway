package com.cafe.apigateway.exception;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":\"%s\", \"message\":\"%s\"}", "G500", "일시적인 오류가 발생했습니다.");
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof TimeoutException) return HttpStatus.GATEWAY_TIMEOUT;
        if (ex instanceof ConnectException) return HttpStatus.BAD_GATEWAY;
        if (ex instanceof ResponseStatusException rse) return HttpStatus.valueOf(rse.getStatusCode().value());
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
