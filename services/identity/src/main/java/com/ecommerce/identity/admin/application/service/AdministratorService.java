package com.ecommerce.identity.admin.application.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.exception.AdministratorException;
import com.ecommerce.identity.admin.application.exception.AdministratorNotAuthorizedException;
import com.ecommerce.identity.admin.application.exception.AdministratorNotFoundException;
import com.ecommerce.identity.admin.application.exception.DuplicateAdministratorException;
import com.ecommerce.identity.admin.application.exception.LastAdministratorException;
import com.ecommerce.identity.admin.application.port.AccountGrantRevocation;
import com.ecommerce.identity.admin.application.port.AdministratorRepository;
import com.ecommerce.identity.admin.domain.Administrator;

@Service
public class AdministratorService {
    private final AdministratorRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<AccountGrantRevocation> grantRevocations;

    public AdministratorService(
            AdministratorRepository repository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<AccountGrantRevocation> grantRevocations) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.grantRevocations = grantRevocations;
    }

    @Transactional
    public Administrator create(UUID actorId, String username, String rawPassword) {
        repository.lockSecurityState();
        repository.lockAdministrators(java.util.List.of(actorId));
        lockActor(actorId, "administrator:write");
        String normalizedUsername = normalizeUsername(username);
        validatePassword(rawPassword);
        Administrator administrator = new Administrator(UUID.randomUUID(), normalizedUsername, true);
        try {
            repository.insert(administrator.id(), normalizedUsername, passwordEncoder.encode(rawPassword));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateAdministratorException();
        }
        repository.audit(actorId, administrator.id(), "ADMINISTRATOR_CREATED");
        return administrator;
    }

    @Transactional
    public void setEnabled(UUID actorId, UUID administratorId, boolean enabled) {
        repository.lockSecurityState();
        repository.lockAdministrators(java.util.List.of(actorId, administratorId));
        lockActor(actorId, "administrator:write");
        requireAdministrator(administratorId);
        repository.setEnabled(administratorId, enabled);
        if (!enabled) {
            AccountGrantRevocation revocationService = grantRevocations.getIfAvailable();
            if (revocationService != null) {
                repository
                        .find(administratorId)
                        .ifPresent(
                                administrator -> revocationService.revoke(administratorId, administrator.username()));
            }
        }
        ensurePrivilegedAdministratorRemains();
        repository.audit(actorId, administratorId, enabled ? "ADMINISTRATOR_ENABLED" : "ADMINISTRATOR_DISABLED");
    }

    @Transactional
    public void replaceRoles(UUID actorId, UUID administratorId, Set<String> roleCodes) {
        repository.lockSecurityState();
        repository.lockAdministrators(java.util.List.of(actorId, administratorId));
        lockActor(actorId, "entitlement:write");
        requireAdministrator(administratorId);
        Set<String> requestedRoles = Set.copyOf(roleCodes);
        if (!repository.roleCodes().containsAll(requestedRoles)) {
            throw new AdministratorException("One or more role codes are unknown.");
        }
        repository.replaceRoles(administratorId, requestedRoles);
        ensurePrivilegedAdministratorRemains();
        repository.audit(actorId, administratorId, "ADMINISTRATOR_ROLES_REPLACED");
    }

    private void lockActor(UUID actorId, String permission) {
        if (!repository.isEnabled(actorId) || !repository.actorHasPermission(actorId, permission)) {
            throw new AdministratorNotAuthorizedException();
        }
    }

    private void requireAdministrator(UUID administratorId) {
        if (repository.find(administratorId).isEmpty()) {
            throw new AdministratorNotFoundException();
        }
    }

    private void ensurePrivilegedAdministratorRemains() {
        if (!repository.hasPrivilegedAdministrator()) {
            throw new LastAdministratorException();
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank() || username.trim().length() > 190) {
            throw new AdministratorException("Username must contain between 1 and 190 characters.");
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < 12
                || rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new AdministratorException(
                    "Password must contain at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }
}
