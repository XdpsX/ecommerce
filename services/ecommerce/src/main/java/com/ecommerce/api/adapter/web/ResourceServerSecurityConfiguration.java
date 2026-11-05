package com.ecommerce.api.adapter.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class ResourceServerSecurityConfiguration {
    @Bean
    JwtDecoder ecommerceJwtDecoder(
            @Value("${identity.jwk-set-uri}") String jwkSetUri,
            @Value("${identity.issuer}") String issuer,
            @Value("${identity.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> subjectAndIssuedAt = jwt -> jwt.getSubject() != null
                        && !jwt.getSubject().isBlank()
                        && jwt.getIssuedAt() != null
                        && jwt.getExpiresAt() != null
                        && validSubject(jwt.getSubject())
                        && validPermissions(jwt.getClaims().get("permissions"))
                        && validStringList(jwt.getClaims().get("roles"))
                        && validScope(jwt.getClaims().get("scope"))
                        && validIssuedAt(jwt.getIssuedAt(), jwt.getExpiresAt())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing identity claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                audienceValidator,
                subjectAndIssuedAt));
        return decoder;
    }

    private boolean validSubject(String subject) {
        try {
            UUID.fromString(subject);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean validPermissions(Object permissions) {
        if (!(permissions instanceof List<?> values)) {
            return false;
        }
        return values.stream().allMatch(value -> value instanceof String);
    }

    private boolean validStringList(Object values) {
        return values instanceof List<?> list && list.stream().allMatch(value -> value instanceof String);
    }

    private boolean validScope(Object scope) {
        return scope instanceof String;
    }

    private boolean validIssuedAt(Instant issuedAt, Instant expiresAt) {
        return !issuedAt.isAfter(Instant.now().plusSeconds(30)) && expiresAt.isAfter(issuedAt);
    }

    @Bean
    JwtAuthenticationConverter ecommerceJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new Converter<>() {
            @Override
            public Collection<GrantedAuthority> convert(Jwt jwt) {
                List<GrantedAuthority> authorities = new ArrayList<>();
                Object permissions = jwt.getClaims().get("permissions");
                if (permissions instanceof List<?> values) {
                    values.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .forEach(value -> authorities.add(new SimpleGrantedAuthority("PERM_" + value)));
                }
                Object scopes = jwt.getClaims().get("scope");
                if (scopes instanceof String value) {
                    for (String scope : value.split(" ")) {
                        if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                    }
                }
                return authorities;
            }
        });
        return converter;
    }

    @Bean
    SecurityFilterChain ecommerceSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter ecommerceJwtAuthenticationConverter,
            ApiProblems problems,
            ObjectProvider<
                            Customizer<
                                    AuthorizeHttpRequestsConfigurer<HttpSecurity>
                                            .AuthorizationManagerRequestMatcherRegistry>>
                    authorizationRules)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorizationRules.orderedStream().forEach(rule -> rule.customize(authorize));
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(ecommerceJwtAuthenticationConverter)));
        return http.build();
    }
}
