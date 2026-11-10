package com.ecommerce.identity.config;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.LocaleResolver;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.authorization.adapter.oauth2.OAuth2Components;
import com.ecommerce.identity.authorization.adapter.oauth2.grant.RefreshTokenLineageService;
import com.ecommerce.identity.authorization.adapter.oauth2.login.AdministratorSubjectResolver;
import com.ecommerce.identity.authorization.adapter.oauth2.persistence.RefreshTokenFamilyRepository;
import com.ecommerce.identity.authorization.adapter.oauth2.persistence.TransactionalOAuth2AuthorizationService;
import com.ecommerce.identity.authorization.adapter.oauth2.token.AccessTokenCustomizer;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "identity", name = "server-enabled", havingValue = "true")
public class AuthorizationServerConfiguration {
    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbc,
            RegisteredClientRepository clients,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            PlatformTransactionManager transactionManager) {
        OAuth2AuthorizationService jdbcService = new JdbcOAuth2AuthorizationService(jdbc, clients);
        return new TransactionalOAuth2AuthorizationService(
                jdbcService, lineage, families, new TransactionTemplate(transactionManager));
    }

    @Bean
    OAuth2AuthorizationConsentService consentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(IdentityProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .build();
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, IdentityProperties properties) {
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder)
                .setJwtValidator(issuerAndTimestampValidator(properties));
        return decoder;
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource, AccessTokenCustomizer customizer) {
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(encoder);
        jwtGenerator.setJwtCustomizer(customizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2RefreshTokenGenerator());
    }

    @Bean
    AuthenticationProvider atomicAuthorizationCodeAuthenticationProvider(
            AdministratorDirectory administrators,
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return OAuth2Components.authorizationCodeProvider(
                administrators, authorizations, tokenGenerator, jdbc, transactionManager);
    }

    @Bean
    RequestCache identityRequestCache(AuthorizationServerSettings settings) {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        // Browser asset/error requests must not replace an in-progress OAuth login.
        requestCache.setRequestMatcher(request -> request.getDispatcherType() == DispatcherType.REQUEST
                && "GET".equals(request.getMethod())
                && settings.getAuthorizationEndpoint().equals(request.getServletPath()));
        return requestCache;
    }

    @Bean(name = "localeResolver")
    LocaleResolver identityLocaleResolver() {
        return OAuth2Components.identityLocaleResolver();
    }

    @Bean
    AuthenticationProvider refreshTokenReplayAuthenticationProvider(
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            Clock identityClock,
            PlatformTransactionManager transactionManager) {
        return OAuth2Components.refreshTokenProvider(
                authorizations, tokenGenerator, lineage, families, identityClock, transactionManager);
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            AdministratorSubjectResolver subjects,
            @Qualifier("refreshTokenReplayAuthenticationProvider") AuthenticationProvider refreshProvider,
            @Qualifier("atomicAuthorizationCodeAuthenticationProvider")
                    AuthenticationProvider authorizationCodeProvider,
            AuthorizationServerSettings settings,
            RequestCache identityRequestCache,
            LocaleResolver identityLocaleResolver)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(
                        authorizationServer,
                        server -> server.oidc(oidc -> oidc.userInfoEndpoint(
                                        userInfo -> userInfo.userInfoMapper(userInfoMapper(subjects))))
                                .tokenEndpoint(token -> token.authenticationProviders(providers -> {
                                    providers.removeIf(provider -> provider
                                                    instanceof
                                                    org.springframework.security.oauth2.server.authorization
                                                            .authentication.OAuth2RefreshTokenAuthenticationProvider
                                            || provider
                                                    instanceof
                                                    org.springframework.security.oauth2.server.authorization
                                                            .authentication
                                                            .OAuth2AuthorizationCodeAuthenticationProvider);
                                    providers.add(refreshProvider);
                                    providers.add(authorizationCodeProvider);
                                })))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        jakarta.servlet.Filter loginFilter = OAuth2Components.authorizationRequestLoginFilter(
                settings.getAuthorizationEndpoint(),
                identityRequestCache,
                new LoginUrlAuthenticationEntryPoint("/login"));
        http.addFilterBefore(loginFilter, AuthorizationFilter.class)
                .addFilterBefore(
                        OAuth2Components.loginLocaleFilter(identityLocaleResolver, settings.getAuthorizationEndpoint()),
                        loginFilter.getClass())
                .addFilterBefore(OAuth2Components.currentAdministratorFilter(subjects), AuthorizationFilter.class);
        return http.build();
    }

    private OAuth2TokenValidator<Jwt> issuerAndTimestampValidator(IdentityProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.getIssuer()), new JwtTimestampValidator(Duration.ofSeconds(30)));
    }

    @Bean
    @Order(3)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            AdministratorSubjectResolver subjects,
            RequestCache identityRequestCache,
            AuthorizationServerSettings settings,
            LocaleResolver identityLocaleResolver)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/login-assets/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .requestCache(cache -> cache.requestCache(identityRequestCache))
                .formLogin(login -> login.loginPage("/login").permitAll());
        http.addFilterBefore(
                        OAuth2Components.loginLocaleFilter(identityLocaleResolver, settings.getAuthorizationEndpoint()),
                        AuthorizationFilter.class)
                .addFilterBefore(OAuth2Components.currentAdministratorFilter(subjects), AuthorizationFilter.class);
        return http.build();
    }

    private Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper(
            AdministratorSubjectResolver subjects) {
        return context -> {
            String username = context.getAuthorization().getPrincipalName();
            String subject = subjects.resolve(username).subject().toString();
            return OidcUserInfo.builder().subject(subject).build();
        };
    }
}
