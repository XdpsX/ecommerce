package com.ecommerce.identity.admin.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ecommerce.identity.admin.domain.Administrator;
import com.ecommerce.identity.admin.domain.Entitlements;

public interface AdministratorRepository {
    void lockSecurityState();

    Optional<Administrator> find(UUID id);

    Optional<Administrator> findByUsername(String username);

    void lockAdministrators(List<UUID> ids);

    Entitlements entitlements(UUID administratorId);

    boolean hasRequiredAdministratorEntitlements(UUID administratorId);

    void insert(UUID id, String username, String passwordHash);

    boolean isEnabled(UUID id);

    boolean actorHasPermission(UUID actorId, String permission);

    void setEnabled(UUID id, boolean enabled);

    Set<String> roleCodes();

    void replaceRoles(UUID administratorId, Set<String> roleCodes);

    boolean hasPrivilegedAdministrator();

    void audit(UUID actorId, UUID targetId, String action);

    String passwordHash(UUID id);

    void lockByUsername(String username);

    boolean lockEnabled(UUID id);

    void auditRefreshReuse(UUID id);
}
