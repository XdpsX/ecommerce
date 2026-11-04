package com.ecommerce.identity.authorization.infrastructure.oauth2;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
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
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.identity.admin.application.AdministratorDirectory;
import com.ecommerce.identity.api.error.ApiProblems;
import com.ecommerce.identity.authorization.infrastructure.grant.RefreshTokenLineageService;
import com.ecommerce.identity.authorization.infrastructure.login.AdministratorSubjectResolver;
import com.ecommerce.identity.authorization.infrastructure.persistence.RefreshTokenFamilyRepository;
import com.ecommerce.identity.authorization.infrastructure.persistence.TransactionalOAuth2AuthorizationService;
import com.ecommerce.identity.authorization.infrastructure.token.AccessTokenCustomizer;
import com.ecommerce.identity.configuration.IdentityProperties;
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
    JwtDecoder identityManagementJwtDecoder(JWKSource<SecurityContext> jwkSource, IdentityProperties properties) {
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        OAuth2TokenValidator<Jwt> issuerAndTimestamp = issuerAndTimestampValidator(properties);
        OAuth2TokenValidator<Jwt> identityAudience = jwt -> jwt.getAudience().contains("identity-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token", "The token is not intended for the identity API.", null));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder)
                .setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTimestamp, identityAudience));
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
    AtomicAuthorizationCodeAuthenticationProvider atomicAuthorizationCodeAuthenticationProvider(
            AdministratorDirectory administrators,
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new AtomicAuthorizationCodeAuthenticationProvider(
                authorizations, tokenGenerator, jdbc, administrators, new TransactionTemplate(transactionManager));
    }

    @Bean
    RequestCache identityRequestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request ->
                request.getDispatcherType() != DispatcherType.ERROR && !"/error".equals(request.getServletPath()));
        return requestCache;
    }

    @Bean
    RefreshTokenReplayAuthenticationProvider refreshTokenReplayAuthenticationProvider(
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<?> tokenGenerator,
            RefreshTokenLineageService lineage,
            RefreshTokenFamilyRepository families,
            Clock identityClock,
            PlatformTransactionManager transactionManager) {
        return new RefreshTokenReplayAuthenticationProvider(
                authorizations,
                tokenGenerator,
                lineage,
                families,
                identityClock,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            AdministratorSubjectResolver subjects,
            RefreshTokenReplayAuthenticationProvider refreshProvider,
            AtomicAuthorizationCodeAuthenticationProvider authorizationCodeProvider,
            AuthorizationServerSettings settings,
            RequestCache identityRequestCache)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(
                        authorizationServer,
                        server -> server.oidc(oidc -> oidc.userInfoEndpoint(
                                        userInfo -> userInfo.userInfoMapper(userInfoMapper(subjects))))
                                .tokenEndpoint(token -> token.authenticationProviders(providers -> {
                                    providers.removeIf(provider -> provider
                                                    instanceof OAuth2RefreshTokenAuthenticationProvider
                                            || provider instanceof OAuth2AuthorizationCodeAuthenticationProvider);
                                    providers.add(refreshProvider);
                                    providers.add(authorizationCodeProvider);
                                })))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        http.addFilterBefore(
                        new AuthorizationRequestLoginFilter(
                                settings.getAuthorizationEndpoint(),
                                identityRequestCache,
                                new LoginUrlAuthenticationEntryPoint("/login")),
                        AuthorizationFilter.class)
                .addFilterBefore(new CurrentAdministratorFilter(subjects), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain managementApiSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter identityJwtConverter,
            ApiProblems problems,
            @Qualifier("identityManagementJwtDecoder") JwtDecoder identityManagementJwtDecoder)
            throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.writeSecurity(request, response, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt.decoder(identityManagementJwtDecoder)
                                .jwtAuthenticationConverter(identityJwtConverter)));
        return http.build();
    }

    private OAuth2TokenValidator<Jwt> issuerAndTimestampValidator(IdentityProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.getIssuer()), new JwtTimestampValidator(Duration.ofSeconds(30)));
    }

    @Bean
    JwtAuthenticationConverter identityJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new Converter<>() {
            @Override
            public Collection<GrantedAuthority> convert(Jwt jwt) {
                List<GrantedAuthority> authorities = new ArrayList<>();
                Object permissions = jwt.getClaims().get("permissions");
                if (permissions instanceof List<?> values) {
                    values.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .forEach(value -> authorities.add(new SimpleGrantedAuthority("PERM_" + value)));
                }
                Object scopes = jwt.getClaims().get("scope");
                if (scopes instanceof String value) {
                    for (String scope : value.split(" ")) {
                        if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                    }
                }
                return authorities;
            }
        });
        return converter;
    }

    @Bean
    @Order(3)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http, AdministratorSubjectResolver subjects, RequestCache identityRequestCache)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(identityRequestCache))
                .formLogin(Customizer.withDefaults());
        http.addFilterBefore(new CurrentAdministratorFilter(subjects), AuthorizationFilter.class);
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
