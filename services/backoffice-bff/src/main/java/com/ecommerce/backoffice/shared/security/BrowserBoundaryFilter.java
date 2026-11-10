package com.ecommerce.backoffice.shared.security;

import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.ecommerce.backoffice.shared.error.ApiProblems;

import reactor.core.publisher.Mono;

public final class BrowserBoundaryFilter implements WebFilter {
    private final ApiProblems problems;
    private final String origin;

    public BrowserBoundaryFilter(ApiProblems problems, String origin) {
        this.problems = problems;
        this.origin = origin;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        var request = exchange.getRequest();
        if (!Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS).contains(request.getMethod())
                && request.getHeaders().getOrigin() != null
                && !origin.equals(request.getHeaders().getOrigin())) {
            return problems.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        var clean = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove("Forwarded");
                    new java.util.ArrayList<>(headers.headerNames())
                            .stream()
                                    .filter(name -> name.toLowerCase().startsWith("x-forwarded-")
                                            || name.toLowerCase().startsWith("x-user-")
                                            || name.toLowerCase().startsWith("x-auth-"))
                                    .forEach(headers::remove);
                }))
                .build();
        Mono<Void> proceed = Mono.defer(() -> chain.filter(clean));
        if (request.getPath().value().equals("/oauth2/authorization/identity")) {
            proceed = clean.getSession()
                    .doOnNext(session -> session.getAttributes()
                            .put("returnTo", safeReturn(request.getQueryParams().getFirst("returnTo"))))
                    .then(proceed);
        }
        return proceed.onErrorResume(
                        io.lettuce.core.RedisException.class,
                        error -> problems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"))
                .onErrorResume(
                        org.springframework.data.redis.RedisConnectionFailureException.class,
                        error -> problems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"))
                .onErrorResume(
                        org.springframework.dao.QueryTimeoutException.class,
                        error -> problems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"));
    }

    public static String safeReturn(String value) {
        if (value == null
                || !value.startsWith("/")
                || value.startsWith("//")
                || value.contains("\\")
                || value.chars().anyMatch(c -> c < 32)) return "/";
        try {
            if (URI.create(value).getRawPath().contains("//")) return "/";
            URI uri = URI.create(value).normalize();
            String path = uri.getRawPath();
            if (uri.isAbsolute()
                    || uri.getRawAuthority() != null
                    || path == null
                    || path.startsWith("//")
                    || path.startsWith("/login")
                    || path.startsWith("/oauth2")
                    || path.startsWith("/bff")
                    || uri.getRawPath().contains("%")) return "/";
            return uri.toASCIIString();
        } catch (IllegalArgumentException error) {
            return "/";
        }
    }
}
