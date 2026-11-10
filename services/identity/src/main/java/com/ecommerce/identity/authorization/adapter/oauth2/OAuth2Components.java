package com.ecommerce.identity.authorization.adapter.oauth2;

import java.util.Locale;

import jakarta.servlet.Filter;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.authorization.adapter.oauth2.grant.RefreshTokenLineageService;
import com.ecommerce.identity.authorization.adapter.oauth2.login.AdministratorSubjectResolver;
import com.ecommerce.identity.authorization.adapter.oauth2.persistence.RefreshTokenFamilyRepository;

/** Narrow factories that let root composition keep protocol collaborators package-private. */
public final class OAuth2Components {
    private OAuth2Components() {}

    public static AuthenticationProvider authorizationCodeProvider(
            AdministratorDirectory administrators,
            org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations,
            org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<?> tokenGenerator,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new AtomicAuthorizationCodeAuthenticationProvider(
                authorizations,
                tokenGenerator,
                jdbc,
                administrators,
                new org.springframework.transaction.support.TransactionTemplate(transactionManager));
    }

    public static AuthenticationProvider refreshTokenProvider(
            org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations,
            org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<?> tokenGenerator,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            java.time.Clock identityClock,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new RefreshTokenReplayAuthenticationProvider(
                authorizations,
                tokenGenerator,
                lineage,
                families,
                identityClock,
                new org.springframework.transaction.support.TransactionTemplate(transactionManager));
    }

    public static Filter authorizationRequestLoginFilter(
            String endpoint, RequestCache requestCache, AuthenticationEntryPoint entryPoint) {
        return new AuthorizationRequestLoginFilter(endpoint, requestCache, entryPoint);
    }

    public static Filter loginLocaleFilter(LocaleResolver resolver, String endpoint) {
        return new LoginLocaleFilter(resolver, endpoint);
    }

    public static Filter currentAdministratorFilter(AdministratorSubjectResolver subjects) {
        return new CurrentAdministratorFilter(subjects);
    }

    public static LocaleResolver identityLocaleResolver() {
        var resolver = new SessionLocaleResolver();
        resolver.setLocaleAttributeName(LoginLocaleFilter.LOCALE_SESSION_ATTRIBUTE);
        resolver.setDefaultLocaleFunction(request -> {
            Locale preference = LoginLocaleFilter.firstSupportedAcceptLanguage(request);
            return preference == null ? Locale.ENGLISH : preference;
        });
        return resolver;
    }
}
