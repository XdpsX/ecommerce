package com.ecommerce.backoffice.configuration;

import java.net.URI;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.web.server.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.*;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.csrf.*;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.*;

import com.ecommerce.backoffice.adapter.web.ApiProblems;

import reactor.core.publisher.Mono;

@Configuration
public class BffSecurityConfiguration {
    // Identity returns JWT access tokens exclusively through the confidential
    // token exchange. Parse exp only to shorten the response's expires_in bound;
    // never derive authentication/authorities from these parsed claims.
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

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClients() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }
    // Gateway TokenRelay supplies a principal but no exchange. Resolve the exchange
    // from Reactor context and read the same session repository used by OAuth login.
    @Bean
    ReactiveOAuth2AuthorizedClientManager relayManager(ServerOAuth2AuthorizedClientRepository clients) {
        return request -> Mono.deferContextual(context -> clients.loadAuthorizedClient(
                request.getClientRegistrationId(),
                request.getPrincipal(),
                ServerWebExchangeContextFilter.getExchange(context).orElseThrow()));
    }

    @Bean
    org.springframework.web.filter.reactive.ServerWebExchangeContextFilter exchangeContext() {
        return new org.springframework.web.filter.reactive.ServerWebExchangeContextFilter();
    }

    @Bean
    org.springframework.web.server.session.WebSessionIdResolver sessionIds(
            @Value("${bff.secure-cookie}") boolean secure) {
        var resolver = new org.springframework.web.server.session.CookieWebSessionIdResolver();
        resolver.setCookieName("BACKOFFICE_SESSION");
        resolver.addCookieInitializer(
                cookie -> cookie.httpOnly(true).secure(secure).sameSite("Lax").path("/"));
        return resolver;
    }

    @Bean
    SecurityWebFilterChain security(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository registrations,
            ServerOAuth2AuthorizedClientRepository clients,
            ApiProblems problems,
            Clock clock,
            @Value("${bff.public-origin}") String origin) {
        URI publicOrigin = URI.create(origin);
        if (publicOrigin.getHost() == null
                || publicOrigin.getRawQuery() != null
                || publicOrigin.getRawFragment() != null
                || !(origin.startsWith("https://") || origin.equals("http://localhost:3000"))) {
            throw new IllegalArgumentException(
                    "A trusted HTTPS public origin is required (localhost:3000 allowed locally).");
        }
        var resolver = new LocaleAwareAuthorizationRequestResolver(registrations);
        var expiry = new WebFilter() {
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                if (!exchange.getRequest().getPath().value().equals("/bff/session")
                        && !exchange.getRequest().getPath().value().startsWith("/api/")) return chain.filter(exchange);
                return exchange.getPrincipal()
                        .ofType(OAuth2AuthenticationToken.class)
                        .flatMap(
                                auth -> clients.<OAuth2AuthorizedClient>loadAuthorizedClient("identity", auth, exchange)
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
        };
        var csrfRepository = new WebSessionServerCsrfTokenRepository();
        var csrfHandler = new ServerCsrfTokenRequestAttributeHandler();

        return http.requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler)
                        .accessDeniedHandler(
                                (exchange, error) -> problems.write(exchange, HttpStatus.FORBIDDEN, "CSRF_INVALID")))
                .authorizeExchange(auth -> auth.pathMatchers(
                                "/bff/csrf",
                                "/bff/logout",
                                "/oauth2/authorization/identity",
                                "/login/oauth2/code/identity")
                        .permitAll()
                        .pathMatchers("/bff/session", "/api/ecommerce/**")
                        .authenticated()
                        .anyExchange()
                        .denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((exchange, error) ->
                                problems.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"))
                        .accessDeniedHandler(
                                (exchange, error) -> problems.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN")))
                .oauth2Login(login -> login.authorizationRequestResolver(resolver)
                        .authorizedClientRepository(clients)
                        .authenticationSuccessHandler((filterExchange, authentication) -> {
                            var exchange = filterExchange.getExchange();
                            return exchange.getSession().flatMap(session -> {
                                Object saved = session.getAttributes().remove("returnTo");
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse()
                                        .getHeaders()
                                        .setLocation(URI.create(origin + (saved instanceof String s ? s : "/")));
                                return exchange.getResponse().setComplete();
                            });
                        })
                        .authenticationFailureHandler((filterExchange, error) -> {
                            var response = filterExchange.getExchange().getResponse();
                            response.setStatusCode(HttpStatus.FOUND);
                            response.getHeaders().setLocation(URI.create(origin + "/login?error=authentication"));
                            return response.setComplete();
                        }))
                .logout(logout -> logout.requiresLogout(
                                ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/bff/logout"))
                        .logoutSuccessHandler((filterExchange, auth) -> filterExchange
                                .getExchange()
                                .getSession()
                                .flatMap(WebSession::invalidate)
                                .then(Mono.defer(() -> {
                                    filterExchange.getExchange().getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                                    return filterExchange
                                            .getExchange()
                                            .getResponse()
                                            .setComplete();
                                }))))
                .addFilterBefore(expiry, SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }
}
