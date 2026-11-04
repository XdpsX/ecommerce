package com.ecommerce.identity.authorization.infrastructure.oauth2;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.identity.admin.application.AdministratorDirectory;

final class AtomicAuthorizationCodeAuthenticationProvider implements AuthenticationProvider {
    private final OAuth2AuthorizationCodeAuthenticationProvider delegate;
    private final JdbcTemplate jdbc;
    private final AdministratorDirectory administrators;
    private final TransactionTemplate transactions;

    AtomicAuthorizationCodeAuthenticationProvider(
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            JdbcTemplate jdbc,
            AdministratorDirectory administrators,
            TransactionTemplate transactions) {
        this.delegate = new OAuth2AuthorizationCodeAuthenticationProvider(authorizations, tokenGenerator);
        this.jdbc = jdbc;
        this.administrators = administrators;
        this.transactions = transactions;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2AuthorizationCodeAuthenticationToken request =
                (OAuth2AuthorizationCodeAuthenticationToken) authentication;
        Result result = transactions.execute(status -> {
            lockAuthorizationCode(request.getCode());
            try {
                return new Result(delegate.authenticate(request), null);
            } catch (OAuth2AuthenticationException exception) {
                // Preserve the framework's persisted code-reuse invalidation before returning invalid_grant.
                return new Result(null, exception);
            }
        });
        if (result == null) {
            throw new IllegalStateException("Authorization code transaction returned no result.");
        }
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.authentication();
    }

    private void lockAuthorizationCode(String code) {
        List<AuthorizationReference> references = jdbc.query(
                "SELECT id, principal_name FROM oauth2_authorization WHERE authorization_code_value = ?",
                (rs, row) -> new AuthorizationReference(rs.getString("id"), rs.getString("principal_name")),
                code);
        if (references.isEmpty()) {
            return;
        }
        AuthorizationReference authorization = references.getFirst();
        administrators.lockByUsername(authorization.principalName());
        jdbc.query(
                "SELECT family_id FROM refresh_token_families WHERE authorization_id = ? FOR UPDATE",
                (rs, row) -> rs.getObject("family_id"),
                authorization.id());
        jdbc.query(
                "SELECT id FROM oauth2_authorization WHERE id = ? AND authorization_code_value = ? FOR UPDATE",
                (rs, row) -> rs.getString("id"),
                authorization.id(),
                code);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private record Result(Authentication authentication, OAuth2AuthenticationException failure) {}

    private record AuthorizationReference(String id, String principalName) {}
}
