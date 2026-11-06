package com.ecommerce.media.adapter.web;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.ecommerce.media.configuration.IdentityProperties;
import com.ecommerce.media.configuration.MediaProperties;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class MediaSecurityConfiguration {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    NimbusJwtDecoder jwtDecoder(IdentityProperties identity, MediaProperties media) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(identity.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(identity.getIssuer());
        OAuth2TokenValidator<Jwt> skew = new JwtTimestampValidator(CLOCK_SKEW);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(media.getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
        OAuth2TokenValidator<Jwt> claims = jwt -> validClaims(jwt, java.time.Instant.now())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, skew, audience, claims));
        return decoder;
    }

    @Bean
    SecurityFilterChain mediaSecurityFilterChain(HttpSecurity http, MediaProperties media, ApiProblems problems)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> problems.writeSecurity(
                request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", true);
        AccessDeniedHandler denied = (request, response, exception) -> problems.writeSecurity(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "The token does not grant this operation.",
                false);
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/admin/media")
                        .access(permission("media:upload"))
                        .requestMatchers(HttpMethod.POST, "/api/admin/media/*/download-link")
                        .access(permission("media:read"))
                        .requestMatchers(HttpMethod.GET, "/api/admin/media/**")
                        .access(permission("media:read"))
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/media/**")
                        .access(permission("media:delete"))
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied))
                .build();
    }

    private AuthorizationManager<RequestAuthorizationContext> permission(String required) {
        return (authentication, context) -> {
            Authentication value = authentication.get();
            if (!(value instanceof JwtAuthenticationToken jwtAuthentication) || !value.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            Jwt token = jwtAuthentication.getToken();
            String scopes = token.getClaimAsString("scope");
            Object rawPermissions = token.getClaims().get("permissions");
            boolean scopePresent =
                    scopes != null && List.of(scopes.split("\\s+")).contains("media.api");
            boolean permissionPresent = rawPermissions instanceof Collection<?> permissions
                    && permissions.stream().anyMatch(required::equals);
            return new AuthorizationDecision(scopePresent && permissionPresent);
        };
    }

    private static boolean validClaims(Jwt token, java.time.Instant now) {
        try {
            UUID.fromString(token.getSubject());
        } catch (RuntimeException exception) {
            return false;
        }
        java.time.Instant issuedAt = token.getIssuedAt();
        java.time.Instant expiresAt = token.getExpiresAt();
        if (issuedAt == null
                || expiresAt == null
                || !expiresAt.isAfter(issuedAt)
                || issuedAt.isAfter(now.plus(CLOCK_SKEW))) {
            return false;
        }
        Object permissions = token.getClaims().get("permissions");
        Object roles = token.getClaims().get("roles");
        return stringList(permissions) && stringList(roles) && token.getClaimAsString("scope") != null;
    }

    private static boolean stringList(Object value) {
        return value instanceof Collection<?> collection && collection.stream().allMatch(String.class::isInstance);
    }
}
