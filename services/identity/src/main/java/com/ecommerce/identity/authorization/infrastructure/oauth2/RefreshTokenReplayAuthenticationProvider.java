package com.ecommerce.identity.authorization.infrastructure.oauth2;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization.Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.identity.authorization.infrastructure.grant.RefreshTokenLineageService;
import com.ecommerce.identity.authorization.infrastructure.persistence.RefreshTokenFamilyRepository;

public class RefreshTokenReplayAuthenticationProvider implements AuthenticationProvider {
    private final OAuth2RefreshTokenAuthenticationProvider delegate;
    private final RefreshTokenLineageService lineage;
    private final RefreshTokenFamilyRepository families;
    private final OAuth2AuthorizationService authorizations;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RefreshTokenReplayAuthenticationProvider(
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            Clock clock,
            TransactionTemplate transactions) {
        this.delegate = new OAuth2RefreshTokenAuthenticationProvider(authorizations, tokenGenerator);
        this.lineage = lineage;
        this.families = families;
        this.authorizations = authorizations;
        this.clock = clock;
        this.transactions = transactions;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2RefreshTokenAuthenticationToken request = (OAuth2RefreshTokenAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientAuthentication = authenticatedClient(request);
        RegisteredClient client = clientAuthentication.getRegisteredClient();
        if (client == null) {
            throw invalidClient();
        }

        String presentedDigest = lineage.digest(request.getRefreshToken());
        RefreshTokenFamilyRepository.Family locatedFamily =
                families.findByTokenDigest(presentedDigest).orElse(null);
        if (locatedFamily == null) {
            throw invalidGrant();
        }
        if (!locatedFamily.registeredClientId().equals(client.getId())) {
            throw invalidGrant();
        }

        Outcome outcome =
                transactions.execute(status -> rotateOrDetectReplay(request, client, locatedFamily, presentedDigest));
        if (outcome == null) {
            throw new IllegalStateException("Refresh transaction returned no result.");
        }
        if (outcome.replayDetected()) {
            throw invalidGrant();
        }
        return outcome.authentication();
    }

    private Outcome rotateOrDetectReplay(
            OAuth2RefreshTokenAuthenticationToken request,
            RegisteredClient client,
            RefreshTokenFamilyRepository.Family locatedFamily,
            String presentedDigest) {
        boolean administratorEnabled = families.lockAdministrator(locatedFamily.administratorId());
        RefreshTokenFamilyRepository.Family family = families.lockFamily(locatedFamily.id())
                .orElseThrow(RefreshTokenReplayAuthenticationProvider::invalidGrant);
        if (!family.registeredClientId().equals(client.getId()) || !"ACTIVE".equals(family.status())) {
            throw invalidGrant();
        }
        RefreshTokenFamilyRepository.History history = families.findHistory(presentedDigest)
                .orElseThrow(RefreshTokenReplayAuthenticationProvider::invalidGrant);

        if (history.consumedAt() != null) {
            revokeAfterReplay(family);
            return new Outcome(null, true);
        }
        if (!administratorEnabled
                || !presentedDigest.equals(family.currentDigest())
                || !history.expiresAt().isAfter(clock.instant())
                || !family.expiresAt().isAfter(clock.instant())) {
            throw invalidGrant();
        }

        Authentication result = delegate.authenticate(request);
        if (!(result instanceof OAuth2AccessTokenAuthenticationToken tokenResponse)) {
            throw new IllegalStateException("The refresh provider returned an unexpected authentication result.");
        }
        OAuth2RefreshToken successor = tokenResponse.getRefreshToken();
        if (successor == null || successor.getIssuedAt() == null || successor.getExpiresAt() == null) {
            throw new IllegalStateException("The refresh provider did not rotate the refresh token.");
        }
        String successorDigest = lineage.digest(successor.getTokenValue());
        if (successorDigest.equals(presentedDigest)) {
            throw new IllegalStateException("The refresh provider reused the consumed refresh token.");
        }
        families.rotate(
                family.id(), presentedDigest, successorDigest, successor.getIssuedAt(), successor.getExpiresAt());
        return new Outcome(tokenResponse, false);
    }

    private void revokeAfterReplay(RefreshTokenFamilyRepository.Family family) {
        Instant now = clock.instant();
        families.revoke(family.id(), now, "REFRESH_TOKEN_REUSE_DETECTED");
        OAuth2Authorization authorization = authorizations.findById(family.authorizationId());
        Token<OAuth2RefreshToken> currentToken = authorization == null ? null : authorization.getRefreshToken();
        if (currentToken != null) {
            OAuth2Authorization revokedAuthorization = OAuth2Authorization.from(authorization)
                    .token(currentToken.getToken(), metadata -> metadata.put(Token.INVALIDATED_METADATA_NAME, true))
                    .build();
            authorizations.save(revokedAuthorization);
        }
        families.auditReuse(family.administratorId());
    }

    private OAuth2ClientAuthenticationToken authenticatedClient(OAuth2RefreshTokenAuthenticationToken request) {
        if (request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
            return client;
        }
        throw invalidClient();
    }

    private static OAuth2AuthenticationException invalidClient() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
    }

    private static OAuth2AuthenticationException invalidGrant() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private record Outcome(Authentication authentication, boolean replayDetected) {}
}
