package com.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.identity.authorization.application.port.ClientRegistration;

@SpringBootTest(properties = "identity.server-enabled=false")
@Testcontainers
class IdentityMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Autowired
    ClientRegistration clientRegistration;

    @Autowired
    RegisteredClientRepository clients;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void appliesMigrationsAndCanRerunFlywayWithoutChangingCatalog() {
        assertCatalogIsMigrated();
        flyway.migrate();
        assertCatalogIsMigrated();
    }

    @Test
    void addingMediaScopeIsIdempotentPreservesClientConfigurationAndRollsBackWithAuditFailure() {
        String clientId = "scope-migration-it-" + UUID.randomUUID();
        RegisteredClient original = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}preserved-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://client.example.test/callback")
                .postLogoutRedirectUri("https://client.example.test/logout")
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        clients.save(original);

        assertThatThrownBy(() -> clientRegistration.addMediaScope("missing-" + clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The named OAuth client does not exist.");
        assertThat(clientRegistration.addMediaScope(clientId)).isTrue();
        assertThat(clientRegistration.addMediaScope(clientId)).isFalse();

        RegisteredClient updated = clients.findByClientId(clientId);
        assertThat(updated.getScopes()).containsExactlyInAnyOrder("openid", "profile", "media.api");
        assertThat(updated.getClientSecret()).isEqualTo(original.getClientSecret());
        assertThat(updated.getRedirectUris()).isEqualTo(original.getRedirectUris());
        assertThat(updated.getPostLogoutRedirectUris()).isEqualTo(original.getPostLogoutRedirectUris());
        assertThat(updated.getAuthorizationGrantTypes()).isEqualTo(original.getAuthorizationGrantTypes());
        assertThat(updated.getClientAuthenticationMethods()).isEqualTo(original.getClientAuthenticationMethods());
        assertThat(updated.getClientSettings()).isEqualTo(original.getClientSettings());
        assertThat(updated.getTokenSettings()).isEqualTo(original.getTokenSettings());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE target_client_id = ? "
                                + "AND action = 'MEDIA_SCOPE_ADDED_TO_CLIENT' AND outcome = 'APPLIED'",
                        Integer.class,
                        clientId))
                .isEqualTo(1);

        jdbcTemplate.execute("CREATE FUNCTION reject_media_scope_audit() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF NEW.action = 'MEDIA_SCOPE_ADDED_TO_CLIENT' THEN RAISE EXCEPTION 'audit unavailable'; "
                + "END IF; RETURN NEW; END $$");
        jdbcTemplate.execute("CREATE TRIGGER reject_media_scope_audit BEFORE INSERT ON administrator_audit "
                + "FOR EACH ROW EXECUTE FUNCTION reject_media_scope_audit()");
        try {
            String secondClientId = "scope-rollback-it-" + UUID.randomUUID();
            RegisteredClient second = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(secondClientId)
                    .clientSecret("{noop}rollback-client-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://client.example.test/callback")
                    .scope("openid")
                    .build();
            clients.save(second);
            assertThatThrownBy(() -> clientRegistration.addMediaScope(secondClientId))
                    .isInstanceOf(RuntimeException.class);
            assertThat(clients.findByClientId(secondClientId).getScopes()).containsExactly("openid");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM administrator_audit WHERE target_client_id = ?",
                            Integer.class,
                            secondClientId))
                    .isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER reject_media_scope_audit ON administrator_audit");
            jdbcTemplate.execute("DROP FUNCTION reject_media_scope_audit()");
        }
    }

    private void assertCatalogIsMigrated() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = TRUE",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = TRUE",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.audience = 'media-api'",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.audience = 'ecommerce-api' AND p.code LIKE 'category:%'",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                                + "AND table_name IN ('oauth2_registered_client', 'oauth2_authorization', "
                                + "'oauth2_authorization_consent')",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                                + "AND table_name IN ('refresh_token_families', 'refresh_token_history')",
                        Integer.class))
                .isEqualTo(2);
    }
}
