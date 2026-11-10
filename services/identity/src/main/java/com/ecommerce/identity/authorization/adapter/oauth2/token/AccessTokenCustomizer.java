package com.ecommerce.identity.authorization.adapter.oauth2.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import com.ecommerce.identity.admin.domain.Permission;
import com.ecommerce.identity.authorization.adapter.oauth2.login.AdministratorSubjectResolver;
import com.ecommerce.identity.config.IdentityProperties;

@Component
public class AccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    private final AdministratorSubjectResolver subjects;
    private final IdentityProperties properties;

    public AccessTokenCustomizer(AdministratorSubjectResolver subjects, IdentityProperties properties) {
        this.subjects = subjects;
        this.properties = properties;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        context.getJwsHeader().algorithm(SignatureAlgorithm.RS256).keyId(properties.getActiveSigningKeyId());
        String username = context.getPrincipal().getName();
        AdministratorSubjectResolver.Identity identity = subjects.resolve(username);
        context.getClaims().subject(identity.subject().toString());

        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        Set<String> scopes = context.getAuthorizedScopes();
        context.getClaims()
                .claim("scope", String.join(" ", scopes.stream().sorted().toList()));
        List<String> audiences = new ArrayList<>();
        if (scopes.contains("ecommerce.api")) {
            audiences.add("ecommerce-api");
        }
        if (scopes.contains("identity.admin")) {
            audiences.add("identity-api");
        }
        if (scopes.contains("media.api")) {
            audiences.add("media-api");
        }
        if (!audiences.isEmpty()) {
            context.getClaims().audience(audiences);
        }
        List<String> permissions = new ArrayList<>(identity.entitlements().permissions().stream()
                .filter(permission -> audiences.contains(permission.audience()))
                .map(Permission::code)
                .sorted()
                .toList());
        List<String> roles = new ArrayList<>(
                identity.entitlements().roles().stream().sorted().toList());
        context.getClaims().claim("roles", roles);
        context.getClaims().claim("permissions", permissions);
    }
}
