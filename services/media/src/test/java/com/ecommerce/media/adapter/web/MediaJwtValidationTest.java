package com.ecommerce.media.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.ecommerce.media.configuration.IdentityProperties;
import com.ecommerce.media.configuration.MediaProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class MediaJwtValidationTest {
    private static final String ISSUER = "https://identity.example.test";
    private RSAKey signingKey;
    private com.sun.net.httpserver.HttpServer jwksServer;
    private MediaSecurityConfiguration security;
    private IdentityProperties identity;
    private MediaProperties media;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048)
                .keyID("media-test-key")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        AtomicReference<byte[]> jwks = new AtomicReference<>(
                new JWKSet(List.of(signingKey.toPublicJWK())).toString().getBytes(StandardCharsets.UTF_8));
        jwksServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = jwks.get();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        jwksServer.start();
        identity = new IdentityProperties();
        identity.setIssuer(ISSUER);
        identity.setJwkSetUri("http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
        media = new MediaProperties();
        security = new MediaSecurityConfiguration();
    }

    @AfterEach
    void stopJwksServer() {
        if (jwksServer != null) jwksServer.stop(0);
    }

    @Test
    void acceptsMatchingIssuerAudienceAndClaimShapes() throws Exception {
        Jwt token = security.jwtDecoder(identity, media)
                .decode(token(Instant.now().plusSeconds(60), "media-api", Instant.now()));

        assertThat(token.getSubject()).isNotBlank();
        assertThat(token.getAudience()).containsExactly("media-api");
    }

    @Test
    void rejectsWrongAudienceAndExpiredBeyondTheThirtySecondSkew() {
        var decoder = security.jwtDecoder(identity, media);

        assertThatThrownBy(() -> decoder.decode(token(Instant.now().plusSeconds(60), "ecommerce-api", Instant.now())))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(
                        Instant.now().minusSeconds(45),
                        "media-api",
                        Instant.now().minusSeconds(300))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void requiresOrderedIssuedAtAndExpirationClaimsWithinClockSkew() {
        var decoder = security.jwtDecoder(identity, media);
        Instant now = Instant.now();

        assertThatThrownBy(() -> decoder.decode(token(null, "media-api", now))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(now.plusSeconds(60), "media-api", null)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(now.plusSeconds(30), "media-api", now.plusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(now.plusSeconds(120), "media-api", now.plusSeconds(60))))
                .isInstanceOf(JwtException.class);
    }

    private String token(Instant expiresAt, String audience, Instant issuedAt) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(UUID.randomUUID().toString())
                    .audience(audience)
                    .claim("scope", "media.api")
                    .claim("permissions", List.of("media:read"))
                    .claim("roles", List.of("SUPER_ADMIN"));
            if (issuedAt != null) claimsBuilder.issueTime(Date.from(issuedAt));
            if (expiresAt != null) claimsBuilder.expirationTime(Date.from(expiresAt));
            JWTClaimsSet claims = claimsBuilder.build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(signingKey.getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
