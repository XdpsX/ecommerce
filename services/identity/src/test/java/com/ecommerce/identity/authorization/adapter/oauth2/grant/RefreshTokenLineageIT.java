package com.ecommerce.identity.authorization.adapter.oauth2.grant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.identity.authorization.adapter.oauth2.persistence.RefreshTokenFamilyRepository;
import com.ecommerce.identity.authorization.adapter.oauth2.persistence.TransactionalOAuth2AuthorizationService;

@SpringBootTest(properties = "identity.server-enabled=false")
@Testcontainers
class RefreshTokenLineageIT {
    private static final String USERNAME = "lineage.admin";
    private static final String RAW_TOKEN = "opaque-refresh-token-value-for-test-only";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RefreshTokenLineageService lineage;

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RefreshTokenFamilyRepository families;

    private UUID administratorId;
    private RegisteredClient client;
    private OAuth2AuthorizationService authorizationService;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        authorizationService = new TransactionalOAuth2AuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbc, clients),
                lineage,
                families,
                new TransactionTemplate(transactionManager));
        administratorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                administratorId,
                USERNAME,
                passwordEncoder.encode("lineage-admin-password"));
        client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("lineage-test-client")
                .clientSecret(passwordEncoder.encode("lineage-test-client-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://client.example.test/callback")
                .scope("openid")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        clients.save(client);
    }

    @AfterEach
    void cleanDatabase() {
        jdbc.update("UPDATE refresh_token_families SET current_token_digest = NULL");
        jdbc.update("DELETE FROM refresh_token_history");
        jdbc.update("DELETE FROM refresh_token_families");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_registered_client WHERE client_id = ?", client.getClientId());
        jdbc.update("DELETE FROM administrator_audit");
        jdbc.update("DELETE FROM administrator_roles");
        jdbc.update("DELETE FROM administrators WHERE id = ?", administratorId);
    }

    @Test
    void persistsOnlyTokenDigestInLineageAndCanReloadAuthorizationFromDatabase() {
        Instant now = Instant.now();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(USERNAME)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .refreshToken(new OAuth2RefreshToken(RAW_TOKEN, now, now.plusSeconds(3600)))
                .build();

        authorizationService.save(authorization);

        String digest = lineage.digest(RAW_TOKEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token_families", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT token_digest FROM refresh_token_history WHERE family_id = "
                                + "(SELECT family_id FROM refresh_token_families WHERE authorization_id = ?)",
                        String.class,
                        authorization.getId()))
                .isEqualTo(digest)
                .isNotEqualTo(RAW_TOKEN);

        OAuth2Authorization reloaded =
                new JdbcOAuth2AuthorizationService(jdbc, clients).findById(authorization.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getRefreshToken().getToken().getTokenValue()).isEqualTo(RAW_TOKEN);
    }
}
