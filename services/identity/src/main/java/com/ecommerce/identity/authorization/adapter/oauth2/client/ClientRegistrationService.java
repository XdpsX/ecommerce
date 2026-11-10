package com.ecommerce.identity.authorization.adapter.oauth2.client;

import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.authorization.application.port.ClientRegistration;
import com.ecommerce.identity.config.IdentityProperties;

@Service
public class ClientRegistrationService implements ClientRegistration {
    private static final Set<String> ALLOWED_SCOPES =
            Set.of("openid", "profile", "ecommerce.api", "identity.admin", "media.api");

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final JdbcTemplate jdbc;
    private final AdministratorDirectory administrators;

    public ClientRegistrationService(
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            IdentityProperties properties,
            JdbcTemplate jdbc,
            AdministratorDirectory administrators) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.jdbc = jdbc;
        this.administrators = administrators;
    }

    @Transactional
    public boolean register(char[] rawSecret) {
        try {
            validateClientConfiguration();
            validateSecret(rawSecret);
            IdentityProperties.Client client = properties.getClient();
            RegisteredClient existing = clients.findByClientId(client.getClientId());
            if (existing != null) {
                if (matches(existing, rawSecret, grants(properties.getClient().isRefreshTokenGrantEnabled()))) {
                    return false;
                }
                if (properties.getClient().isRefreshTokenGrantEnabled()
                        && matches(existing, rawSecret, Set.of(AuthorizationGrantType.AUTHORIZATION_CODE))) {
                    jdbc.update(
                            "UPDATE oauth2_registered_client SET authorization_grant_types = ? WHERE id = ?",
                            "authorization_code,refresh_token",
                            existing.getId());
                    return true;
                }
                throw new IllegalStateException(
                        "The existing client differs from configured values; refusing to overwrite it.");
            }
            RegisteredClient.Builder builder = RegisteredClient.withId(
                            UUID.randomUUID().toString())
                    .clientId(client.getClientId())
                    .clientName(client.getClientName())
                    .clientIdIssuedAt(java.time.Instant.now())
                    .clientSecret(passwordEncoder.encode(CharBuffer.wrap(rawSecret)))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(5))
                            .refreshTokenTimeToLive(Duration.ofHours(8))
                            .reuseRefreshTokens(false)
                            .build());
            grants(properties.getClient().isRefreshTokenGrantEnabled()).forEach(builder::authorizationGrantType);
            client.getRedirectUris().forEach(builder::redirectUri);
            client.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
            client.getScopes().forEach(builder::scope);
            clients.save(builder.build());
            return true;
        } finally {
            if (rawSecret != null) {
                Arrays.fill(rawSecret, '\0');
            }
        }
    }

    @Transactional
    public boolean addMediaScope(String clientId) {
        String registeredClientId = jdbc.query(
                "SELECT id FROM oauth2_registered_client WHERE client_id = ? FOR UPDATE",
                result -> result.next() ? result.getString("id") : null,
                clientId);
        if (registeredClientId == null) {
            throw new IllegalArgumentException("The named OAuth client does not exist.");
        }
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null
                || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            throw new IllegalArgumentException(
                    "The named client must be an existing confidential authorization-code client.");
        }
        if (client.getScopes().contains("media.api")) {
            return false;
        }
        Set<String> updatedScopes = new java.util.TreeSet<>(client.getScopes());
        updatedScopes.add("media.api");
        jdbc.update(
                "UPDATE oauth2_registered_client SET scopes = ? WHERE id = ?",
                String.join(",", updatedScopes),
                registeredClientId);
        administrators.auditClientScopeChange(clientId);
        return true;
    }

    private boolean matches(
            RegisteredClient existing, char[] rawSecret, Set<AuthorizationGrantType> expectedGrantTypes) {
        IdentityProperties.Client configured = properties.getClient();
        return existing.getClientSecret() != null
                && passwordEncoder.matches(CharBuffer.wrap(rawSecret), existing.getClientSecret())
                && existing.getClientName().equals(configured.getClientName())
                && existing.getRedirectUris().equals(configured.getRedirectUris())
                && existing.getPostLogoutRedirectUris().equals(configured.getPostLogoutRedirectUris())
                && existing.getScopes().equals(configured.getScopes())
                && existing.getAuthorizationGrantTypes().equals(expectedGrantTypes)
                && existing.getClientAuthenticationMethods()
                        .equals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC))
                && existing.getClientSettings().isRequireProofKey()
                && !existing.getClientSettings().isRequireAuthorizationConsent()
                && existing.getTokenSettings().getAccessTokenTimeToLive().equals(Duration.ofMinutes(5))
                && existing.getTokenSettings().getRefreshTokenTimeToLive().equals(Duration.ofHours(8))
                && !existing.getTokenSettings().isReuseRefreshTokens();
    }

    private Set<AuthorizationGrantType> grants(boolean refreshTokenEnabled) {
        return refreshTokenEnabled
                ? Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN)
                : Set.of(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    private void validateClientConfiguration() {
        IdentityProperties.Client client = properties.getClient();
        if (client.getClientId() == null
                || client.getClientId().isBlank()
                || client.getClientId().length() > 100) {
            throw new IllegalStateException("A valid identity.client.client-id is required.");
        }
        if (client.getClientName() == null
                || client.getClientName().isBlank()
                || client.getClientName().length() > 200) {
            throw new IllegalStateException("A valid identity.client.client-name is required.");
        }
        if (client.getRedirectUris().isEmpty()
                || !ALLOWED_SCOPES.containsAll(client.getScopes())
                || !client.getScopes().contains("openid")) {
            throw new IllegalStateException("At least one redirect URI and the approved client scopes are required.");
        }
        client.getRedirectUris().forEach(this::validateUri);
        client.getPostLogoutRedirectUris().forEach(this::validateUri);
    }

    private void validateUri(String value) {
        URI uri = URI.create(value);
        boolean localhost = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!uri.isAbsolute()
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme())
                        || (localhost && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new IllegalStateException(
                    "Redirect URIs must be absolute HTTPS URLs (HTTP is allowed only on localhost).");
        }
    }

    private void validateSecret(char[] secret) {
        if (secret == null) {
            throw new IllegalArgumentException("Client secret must contain 32 to 72 UTF-8 bytes.");
        }
        int byteLength = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret)).remaining();
        if (byteLength < 32 || byteLength > 72) {
            throw new IllegalArgumentException("Client secret must contain 32 to 72 UTF-8 bytes.");
        }
    }
}
