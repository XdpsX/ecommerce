package com.ecommerce.api.adapter.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.api.adapter.web.support.SecurityProbeConfiguration;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityProbeConfiguration.class)
@Testcontainers
class ResourceServerSecurityIT {
    private static final String ISSUER = "https://identity.test";

    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private static RSAKey signingKey;
    private static HttpServer jwksServer;

    @Autowired
    MockMvc mvc;

    @AfterAll
    static void stopJwks() {
        jwksServer.stop(0);
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        startJwks();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("identity.issuer", () -> ISSUER);
        registry.add(
                "identity.jwk-set-uri",
                () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
        registry.add("identity.audience", () -> "ecommerce-api");
    }

    private static synchronized void startJwks() {
        if (jwksServer != null) {
            return;
        }
        try {
            signingKey = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID("ecommerce-it-key")
                    .generate();
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] publicJwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            jwksServer.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, publicJwks.length);
                exchange.getResponseBody().write(publicJwks);
                exchange.close();
            });
            jwksServer.start();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start the test JWKS endpoint.", exception);
        }
    }

    @Test
    void acceptsValidSignatureIssuerAudienceAndPermission() throws Exception {
        mvc.perform(get("/api/test/security-probe")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + token(ISSUER, "ecommerce-api", List.of("test-probe:read"), "ecommerce.api")))
                .andExpect(status().isOk());
    }

    @Test
    void scopesAndRolesDoNotReplacePermission() throws Exception {
        mvc.perform(get("/api/test/security-probe")
                        .header(
                                "Authorization",
                                "Bearer " + token(ISSUER, "ecommerce-api", List.of(), "ecommerce.api")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void rejectsWrongIssuerAudienceAndSessionCookie() throws Exception {
        mvc.perform(get("/api/test/security-probe")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + token(
                                                "https://other.test",
                                                "ecommerce-api",
                                                List.of("test-probe:read"),
                                                "ecommerce.api")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/security-probe")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + token(ISSUER, "identity-api", List.of("test-probe:read"), "identity.admin")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/security-probe").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "ignored")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void securityFilterDeniesUnknownApiRoutesWithTheSameProblemContract() throws Exception {
        mvc.perform(get("/api/no-such-route")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + token(ISSUER, "ecommerce-api", List.of("test-probe:read"), "ecommerce.api")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void validatesRequestBodiesAndReturnsSafeFieldErrors() throws Exception {
        String bearer = "Bearer " + token(ISSUER, "ecommerce-api", List.of("test-probe:read"), "ecommerce.api");
        mvc.perform(post("/api/test/validation-probe")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse\",\"code\":\"A1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Warehouse"));

        String invalidBody = "{\"name\":\"\",\"code\":\"this-value-is-way-too-long\"}";
        var invalid = mvc.perform(post("/api/test/validation-probe")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].pointer", containsInAnyOrder("/name", "/code")))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(invalid.getResponse().getContentAsString())
                .doesNotContain("this-value-is-way-too-long", "Warehouse", "rejectedValue", "SQL");

        var malformed = mvc.perform(post("/api/test/validation-probe")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(malformed.getResponse().getContentAsString())
                .doesNotContain("not-json", "JsonParseException", "SQL");
    }

    private String token(String issuer, String audience, List<String> permissions, String scope) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .claim("roles", List.of("SUPER_ADMIN"))
                .claim("permissions", permissions)
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .keyId("ecommerce-it-key")
                        .build(),
                claims));
        return jwt.getTokenValue();
    }
}
