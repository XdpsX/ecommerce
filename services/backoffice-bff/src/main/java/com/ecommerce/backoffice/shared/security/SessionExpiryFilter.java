package com.ecommerce.backoffice.shared.security;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.ecommerce.backoffice.shared.error.ApiProblems;

import reactor.core.publisher.Mono;

public final class SessionExpiryFilter implements WebFilter {
    private final ServerOAuth2AuthorizedClientRepository clients;
    private final ApiProblems problems;
    private final Clock clock;

    public SessionExpiryFilter(ServerOAuth2AuthorizedClientRepository clients, ApiProblems problems, Clock clock) {
        this.clients = clients;
        this.problems = problems;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().equals("/bff/session")
                && !exchange.getRequest().getPath().value().startsWith("/api/")) return chain.filter(exchange);
        return exchange.getPrincipal()
                .ofType(OAuth2AuthenticationToken.class)
                .flatMap(auth -> clients.<OAuth2AuthorizedClient>loadAuthorizedClient("identity", auth, exchange)
                        .filter(client -> usableUntil(client).isAfter(clock.instant()))
                        .map(client -> true)
                        .defaultIfEmpty(false))
                .defaultIfEmpty(false)
                .flatMap(valid -> valid
                        ? chain.filter(exchange)
                        : exchange.getSession()
                                .flatMap(session -> session.invalidate())
                                .then(problems.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED")));
    }

    private static java.time.Instant usableUntil(OAuth2AuthorizedClient client) {
        try {
            var responseExpiry = client.getAccessToken().getExpiresAt();
            var jwtExpiry = com.nimbusds.jwt.JWTParser.parse(
                            client.getAccessToken().getTokenValue())
                    .getJWTClaimsSet()
                    .getExpirationTime()
                    .toInstant();
            return responseExpiry == null || jwtExpiry.isBefore(responseExpiry) ? jwtExpiry : responseExpiry;
        } catch (java.text.ParseException | NullPointerException invalid) {
            return java.time.Instant.EPOCH;
        }
    }
}
