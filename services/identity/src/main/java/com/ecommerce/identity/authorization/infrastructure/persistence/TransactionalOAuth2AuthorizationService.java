package com.ecommerce.identity.authorization.infrastructure.persistence;

import java.time.Instant;

import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.identity.authorization.infrastructure.grant.RefreshTokenLineageService;

public class TransactionalOAuth2AuthorizationService implements OAuth2AuthorizationService {
    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenLineageService lineage;
    private final TransactionTemplate transactions;
    private final RefreshTokenFamilyRepository families;

    public TransactionalOAuth2AuthorizationService(
            OAuth2AuthorizationService delegate,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            TransactionTemplate transactions) {
        this.delegate = delegate;
        this.lineage = lineage;
        this.transactions = transactions;
        this.families = families;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> {
            OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
            if (refresh == null || !refresh.isActive()) {
                families.revokeForAuthorization(authorization.getId(), Instant.now(), "CLIENT_REVOKED");
            }
            lineage.linkInitialGrant(authorization);
            delegate.save(authorization);
        });
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> {
            families.revokeForAuthorization(authorization.getId(), Instant.now(), "AUTHORIZATION_REMOVED");
            delegate.remove(authorization);
        });
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
