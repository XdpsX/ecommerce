package com.ecommerce.identity.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.ecommerce.identity.shared.error.ApiProblems;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@ConditionalOnProperty(prefix = "identity", name = "server-enabled", havingValue = "true")
public class IdentityManagementSecurityConfiguration {
    @Bean
    JwtDecoder identityManagementJwtDecoder(JWKSource<SecurityContext> jwkSource, IdentityProperties properties) {
        JwtDecoder decoder = org.springframework.security.config.annotation.web.configuration
                .OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        OAuth2TokenValidator<Jwt> issuerAndTimestamp = issuerAndTimestampValidator(properties);
        OAuth2TokenValidator<Jwt> identityAudience = jwt -> jwt.getAudience().contains("identity-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token", "The token is not intended for the identity API.", null));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder)
                .setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTimestamp, identityAudience));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter identityJwtConverter() {
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
    @Order(2)
    SecurityFilterChain managementApiSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter identityJwtConverter,
            ApiProblems problems,
            @Qualifier("identityManagementJwtDecoder") JwtDecoder identityManagementJwtDecoder)
            throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt.decoder(identityManagementJwtDecoder)
                                .jwtAuthenticationConverter(identityJwtConverter)));
        return http.build();
    }

    private OAuth2TokenValidator<Jwt> issuerAndTimestampValidator(IdentityProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.getIssuer()), new JwtTimestampValidator(Duration.ofSeconds(30)));
    }
}
