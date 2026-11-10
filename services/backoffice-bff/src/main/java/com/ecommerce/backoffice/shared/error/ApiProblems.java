package com.ecommerce.backoffice.shared.error;

import java.nio.charset.StandardCharsets;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class ApiProblems {
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code) {
        return Mono.defer(() -> {
            var response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            response.getHeaders().setCacheControl("no-store");
            String body =
                    "{\"type\":\"urn:ecommerce:problem:" + code.toLowerCase().replace('_', '-') + "\",\"title\":\""
                            + status.getReasonPhrase() + "\",\"status\":" + status.value()
                            + ",\"detail\":\"The request could not be completed.\",\"code\":\"" + code + "\"}";
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        });
    }
}
