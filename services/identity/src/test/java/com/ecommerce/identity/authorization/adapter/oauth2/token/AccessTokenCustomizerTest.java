package com.ecommerce.identity.authorization.adapter.oauth2.token;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import com.ecommerce.identity.admin.domain.Entitlements;
import com.ecommerce.identity.admin.domain.Permission;
import com.ecommerce.identity.authorization.adapter.oauth2.login.AdministratorSubjectResolver;
import com.ecommerce.identity.config.IdentityProperties;

class AccessTokenCustomizerTest {
    @Test
    void emitsMediaAudienceOnlyForMediaScopeAndFiltersPermissionsByAudience() {
        UUID administratorId = UUID.randomUUID();
        AdministratorSubjectResolver subjects = mock(AdministratorSubjectResolver.class);
        IdentityProperties properties = new IdentityProperties();
        properties.setActiveSigningKeyId("test-key");
        when(subjects.resolve("admin"))
                .thenReturn(new AdministratorSubjectResolver.Identity(
                        administratorId,
                        new Entitlements(
                                Set.of("SUPER_ADMIN"),
                                Set.of(
                                        new Permission("media:read", "media-api"),
                                        new Permission("administrator:read", "identity-api")))));
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwsHeader.Builder header = mock(JwsHeader.Builder.class);
        when(header.algorithm(org.mockito.ArgumentMatchers.any())).thenReturn(header);
        when(header.keyId(org.mockito.ArgumentMatchers.anyString())).thenReturn(header);
        JwtClaimsSet.Builder claims = mock(JwtClaimsSet.Builder.class);
        when(context.getJwsHeader()).thenReturn(header);
        when(context.getClaims()).thenReturn(claims);
        when(context.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken("admin", "secret"));
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("media.api"));

        new AccessTokenCustomizer(subjects, properties).customize(context);

        verify(claims).audience(List.of("media-api"));
        verify(claims).claim("scope", "media.api");
        verify(claims).claim("permissions", List.of("media:read"));
        verify(claims).claim("roles", List.of("SUPER_ADMIN"));
    }

    @Test
    void omitsMediaAudienceAndPermissionsWhenMediaScopeWasNotAuthorized() {
        UUID administratorId = UUID.randomUUID();
        AdministratorSubjectResolver subjects = mock(AdministratorSubjectResolver.class);
        IdentityProperties properties = new IdentityProperties();
        properties.setActiveSigningKeyId("test-key");
        when(subjects.resolve("admin"))
                .thenReturn(new AdministratorSubjectResolver.Identity(
                        administratorId,
                        new Entitlements(
                                Set.of("SUPER_ADMIN"),
                                Set.of(
                                        new Permission("media:read", "media-api"),
                                        new Permission("administrator:read", "identity-api")))));
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwsHeader.Builder header = mock(JwsHeader.Builder.class);
        when(header.algorithm(org.mockito.ArgumentMatchers.any())).thenReturn(header);
        when(header.keyId(org.mockito.ArgumentMatchers.anyString())).thenReturn(header);
        JwtClaimsSet.Builder claims = mock(JwtClaimsSet.Builder.class);
        when(context.getJwsHeader()).thenReturn(header);
        when(context.getClaims()).thenReturn(claims);
        when(context.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken("admin", "secret"));
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("identity.admin"));

        new AccessTokenCustomizer(subjects, properties).customize(context);

        verify(claims).audience(List.of("identity-api"));
        verify(claims).claim("scope", "identity.admin");
        verify(claims).claim("permissions", List.of("administrator:read"));
        verify(claims, never()).audience(List.of("identity-api", "media-api"));
    }
}
