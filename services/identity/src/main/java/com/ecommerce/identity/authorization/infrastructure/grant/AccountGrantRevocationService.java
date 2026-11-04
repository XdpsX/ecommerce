package com.ecommerce.identity.authorization.infrastructure.grant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.AccountGrantRevocation;
import com.ecommerce.identity.authorization.infrastructure.persistence.RefreshTokenFamilyRepository;

@Service
@ConditionalOnProperty(prefix = "identity", name = "server-enabled", havingValue = "true")
public class AccountGrantRevocationService implements AccountGrantRevocation {
    private final JdbcTemplate jdbc;
    private final OAuth2AuthorizationService authorizations;
    private final RefreshTokenFamilyRepository families;

    public AccountGrantRevocationService(
            JdbcTemplate jdbc, OAuth2AuthorizationService authorizations, RefreshTokenFamilyRepository families) {
        this.jdbc = jdbc;
        this.authorizations = authorizations;
        this.families = families;
    }

    @Transactional
    public void revoke(UUID administratorId, String username) {
        families.revokeAllForAdministrator(administratorId, Instant.now(), "ACCOUNT_DISABLED");
        List<String> ids = jdbc.query(
                "SELECT id FROM oauth2_authorization WHERE principal_name = ? ORDER BY id",
                (rs, row) -> rs.getString(1),
                username);
        for (String id : ids) {
            OAuth2Authorization authorization = authorizations.findById(id);
            if (authorization == null) {
                continue;
            }
            OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
            OAuth2Authorization.Token<OAuth2AuthorizationCode> code =
                    authorization.getToken(OAuth2AuthorizationCode.class);
            if (code != null && !code.isInvalidated()) {
                builder.token(
                        code.getToken(),
                        metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
            }
            OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
            if (refresh != null && !refresh.isInvalidated()) {
                builder.token(
                        refresh.getToken(),
                        metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
            }
            authorizations.save(builder.build());
        }
    }
}
