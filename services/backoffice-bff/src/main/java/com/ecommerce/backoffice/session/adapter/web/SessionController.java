package com.ecommerce.backoffice.session.adapter.web;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
public class SessionController {
    @GetMapping("/bff/session")
    public Mono<Map<String, String>> session(Authentication authentication) {
        return Mono.just(Map.of("subject", authentication.getName()));
    }

    @GetMapping("/bff/csrf")
    public Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        return token.map(value -> Map.of("token", value.getToken(), "headerName", value.getHeaderName()));
    }
}
