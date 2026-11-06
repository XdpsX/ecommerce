package com.ecommerce.identity.authorization.adapter.oauth2.grant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.admin.domain.Administrator;

@Service
public class RefreshTokenLineageService {
    private final JdbcTemplate jdbc;
    private final AdministratorDirectory administrators;
    private final RegisteredClientRepository clients;

    public RefreshTokenLineageService(
            JdbcTemplate jdbc, AdministratorDirectory administrators, RegisteredClientRepository clients) {
        this.jdbc = jdbc;
        this.administrators = administrators;
        this.clients = clients;
    }

    public void linkInitialGrant(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken == null
                || !AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())) {
            return;
        }
        Boolean alreadyLinked = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM refresh_token_families WHERE authorization_id = ?)",
                Boolean.class,
                authorization.getId());
        if (Boolean.TRUE.equals(alreadyLinked)) {
            return;
        }
        Administrator administrator = administrators
                .findByUsername(authorization.getPrincipalName())
                .orElseThrow(RefreshTokenLineageService::invalidGrant);
        boolean enabled = administrators.lockEnabled(administrator.id());
        if (!Boolean.TRUE.equals(enabled)) {
            throw invalidGrant();
        }
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null) {
            throw new IllegalStateException("The authorization references an unknown registered client.");
        }
        OAuth2RefreshToken token = refreshToken.getToken();
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        if (issuedAt == null || expiresAt == null) {
            throw new IllegalStateException("The initial refresh token is missing its lifetime.");
        }
        UUID familyId = UUID.randomUUID();
        String tokenDigest = digest(token.getTokenValue());
        jdbc.update(
                "INSERT INTO refresh_token_families(family_id, authorization_id, administrator_id, "
                        + "registered_client_id, status, current_expires_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?)",
                familyId,
                authorization.getId(),
                administrator.id(),
                client.getId(),
                Timestamp.from(expiresAt));
        jdbc.update(
                "INSERT INTO refresh_token_history(token_digest, family_id, issued_at, expires_at) VALUES (?, ?, ?, ?)",
                tokenDigest,
                familyId,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt));
        jdbc.update(
                "UPDATE refresh_token_families SET current_token_digest = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE family_id = ?",
                tokenDigest,
                familyId);
    }

    public String digest(String rawToken) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static OAuth2AuthenticationException invalidGrant() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
    }
}
