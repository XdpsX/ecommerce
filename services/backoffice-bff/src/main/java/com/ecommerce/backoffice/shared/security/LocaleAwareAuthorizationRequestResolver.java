package com.ecommerce.backoffice.shared.security;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

public final class LocaleAwareAuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {
    private static final Pattern SUPPORTED_TAG = Pattern.compile("(?i)^(en|vi)(?:-[a-z]{2})?$");

    private final ServerOAuth2AuthorizationRequestResolver delegate;

    public LocaleAwareAuthorizationRequestResolver(ReactiveClientRegistrationRepository registrations) {
        var resolver = new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
        resolver.setAuthorizationRequestCustomizer(
                org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers.withPkce());
        this.delegate = resolver;
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        return resolve(exchange, delegate.resolve(exchange));
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
        return resolve(exchange, delegate.resolve(exchange, clientRegistrationId));
    }

    private Mono<OAuth2AuthorizationRequest> resolve(
            ServerWebExchange exchange, Mono<OAuth2AuthorizationRequest> request) {
        String locale =
                firstSupportedLocale(exchange.getRequest().getQueryParams().getFirst("ui_locales"));
        if (locale == null) return request;

        return request.map(authorizationRequest -> {
            String authorizationUri = UriComponentsBuilder.fromUriString(
                            authorizationRequest.getAuthorizationRequestUri())
                    .replaceQueryParam("ui_locales")
                    .queryParam("ui_locales", locale)
                    .build(true)
                    .toUriString();
            return OAuth2AuthorizationRequest.from(authorizationRequest)
                    .authorizationRequestUri(authorizationUri)
                    .additionalParameters(parameters -> parameters.put("ui_locales", locale))
                    .build();
        });
    }

    static String firstSupportedLocale(String locales) {
        if (locales == null || locales.length() > 128) return null;

        for (String candidate : locales.trim().split("\\s+", 9)) {
            if (!SUPPORTED_TAG.matcher(candidate).matches()) continue;
            return Locale.forLanguageTag(candidate).getLanguage().toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
