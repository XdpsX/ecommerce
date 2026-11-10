package com.ecommerce.backoffice.shared.error;

import java.net.ConnectException;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class RestErrorHandler implements WebExceptionHandler {
    private final ApiProblems problems;

    public RestErrorHandler(ApiProblems problems) {
        this.problems = problems;
    }

    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getResponse().isCommitted() || !(path.startsWith("/bff/") || path.startsWith("/api/")))
            return Mono.error(error);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (error instanceof ResponseStatusException rejected) {
            status = HttpStatus.resolve(rejected.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            if (rejected.getHeaders().getAllow() != null
                    && !rejected.getHeaders().getAllow().isEmpty())
                exchange.getResponse()
                        .getHeaders()
                        .setAllow(rejected.getHeaders().getAllow());
        } else if (NestedExceptionUtils.getMostSpecificCause(error) instanceof ConnectException)
            status = HttpStatus.BAD_GATEWAY;
        String code = switch (status) {
            case BAD_GATEWAY -> "BAD_GATEWAY";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            default -> status.is4xxClientError() ? "REQUEST_REJECTED" : "INTERNAL_ERROR";
        };
        return problems.write(exchange, status, code);
    }
}
