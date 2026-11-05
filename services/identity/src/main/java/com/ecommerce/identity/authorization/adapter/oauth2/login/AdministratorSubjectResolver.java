package com.ecommerce.identity.authorization.adapter.oauth2.login;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.admin.domain.Administrator;
import com.ecommerce.identity.admin.domain.Entitlements;

@Service
public class AdministratorSubjectResolver {
    private final AdministratorDirectory administrators;

    public AdministratorSubjectResolver(AdministratorDirectory administrators) {
        this.administrators = administrators;
    }

    @Transactional(readOnly = true)
    public Identity resolve(String username) {
        Administrator administrator = administrators
                .findByUsername(username)
                .filter(Administrator::enabled)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT)));
        Entitlements entitlements = administrators.entitlements(administrator.id());
        return new Identity(administrator.id(), entitlements);
    }

    public record Identity(UUID subject, Entitlements entitlements) {}
}
