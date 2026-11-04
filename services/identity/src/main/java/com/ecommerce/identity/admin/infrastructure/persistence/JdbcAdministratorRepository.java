package com.ecommerce.identity.admin.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ecommerce.identity.admin.application.AdministratorRepository;
import com.ecommerce.identity.admin.domain.Administrator;
import com.ecommerce.identity.admin.domain.Entitlements;
import com.ecommerce.identity.admin.domain.Permission;

@Repository
public class JdbcAdministratorRepository implements AdministratorRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdministratorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockSecurityState() {
        jdbc.queryForObject(
                "SELECT singleton_id FROM identity_security_state WHERE singleton_id = 1 FOR UPDATE", Integer.class);
    }

    public Optional<Administrator> find(UUID id) {
        return jdbc
                .query(
                        "SELECT id, username, enabled FROM administrators WHERE id = ?",
                        (rs, row) -> new Administrator(
                                rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("enabled")),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<Administrator> findByUsername(String username) {
        return jdbc
                .query(
                        "SELECT id, username, enabled FROM administrators WHERE username = ?",
                        (rs, row) -> new Administrator(
                                rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("enabled")),
                        username)
                .stream()
                .findFirst();
    }

    public void lockAdministrators(List<UUID> ids) {
        ids.stream()
                .distinct()
                .sorted()
                .forEach(id -> jdbc.query("SELECT id FROM administrators WHERE id = ? FOR UPDATE", rs -> {}, id));
    }

    public Entitlements entitlements(UUID administratorId) {
        Set<String> roles = Set.copyOf(jdbc.query(
                "SELECT r.code FROM roles r JOIN administrator_roles ar ON ar.role_id = r.id "
                        + "WHERE ar.administrator_id = ? ORDER BY r.code",
                (rs, row) -> rs.getString(1),
                administratorId));
        Set<Permission> permissions = Set.copyOf(jdbc.query(
                "SELECT DISTINCT p.code, p.audience FROM permissions p "
                        + "JOIN role_permissions rp ON rp.permission_id = p.id "
                        + "JOIN administrator_roles ar ON ar.role_id = rp.role_id "
                        + "WHERE ar.administrator_id = ? ORDER BY p.audience, p.code",
                (rs, row) -> new Permission(rs.getString("code"), rs.getString("audience")),
                administratorId));
        return new Entitlements(roles, permissions);
    }

    public boolean hasRequiredAdministratorEntitlements(UUID administratorId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM administrators a "
                        + "JOIN administrator_roles ar ON ar.administrator_id = a.id "
                        + "JOIN roles r ON r.id = ar.role_id "
                        + "WHERE a.id = ? AND a.enabled AND r.code = 'SUPER_ADMIN' "
                        + "AND NOT EXISTS (SELECT required.code FROM (VALUES "
                        + "('administrator:read'), ('administrator:write'), ('entitlement:read'), ('entitlement:write')"
                        + ") required(code) WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE rp.role_id = r.id AND p.code = required.code AND p.audience = 'identity-api'))) ",
                Boolean.class,
                administratorId));
    }

    public void insert(UUID id, String username, String passwordHash) {
        jdbc.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                id,
                username,
                passwordHash);
    }

    public boolean isEnabled(UUID id) {
        return find(id).map(Administrator::enabled).orElse(false);
    }

    public boolean actorHasPermission(UUID actorId, String permission) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM administrators a "
                        + "JOIN administrator_roles ar ON ar.administrator_id = a.id "
                        + "JOIN roles r ON r.id = ar.role_id "
                        + "JOIN role_permissions rp ON rp.role_id = r.id "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE a.id = ? AND a.enabled AND r.code = 'SUPER_ADMIN' "
                        + "AND p.code = ? AND p.audience = 'identity-api')",
                Boolean.class,
                actorId,
                permission));
    }

    public void setEnabled(UUID id, boolean enabled) {
        jdbc.update("UPDATE administrators SET enabled = ? WHERE id = ?", enabled, id);
    }

    public Set<String> roleCodes() {
        return Set.copyOf(jdbc.query("SELECT code FROM roles ORDER BY code", (rs, row) -> rs.getString(1)));
    }

    public void replaceRoles(UUID administratorId, Set<String> roleCodes) {
        jdbc.update("DELETE FROM administrator_roles WHERE administrator_id = ?", administratorId);
        roleCodes.stream()
                .sorted()
                .forEach(code -> jdbc.update(
                        "INSERT INTO administrator_roles(administrator_id, role_id) "
                                + "SELECT ?, id FROM roles WHERE code = ?",
                        administratorId,
                        code));
    }

    public boolean hasPrivilegedAdministrator() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM administrators a "
                        + "JOIN administrator_roles ar ON ar.administrator_id = a.id "
                        + "JOIN roles r ON r.id = ar.role_id "
                        + "WHERE a.enabled AND r.code = 'SUPER_ADMIN' "
                        + "AND NOT EXISTS (SELECT required.code FROM (VALUES "
                        + "('administrator:read'), ('administrator:write'), ('entitlement:read'), ('entitlement:write')"
                        + ") required(code) WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE rp.role_id = r.id AND p.code = required.code AND p.audience = 'identity-api'))) ",
                Boolean.class));
    }

    public void audit(UUID actorId, UUID targetId, String action) {
        jdbc.update(
                "INSERT INTO administrator_audit(id, actor_administrator_id, target_administrator_id, action, outcome) "
                        + "VALUES (?, ?, ?, ?, 'SUCCESS')",
                UUID.randomUUID(),
                actorId,
                targetId,
                action);
    }

    public String passwordHash(UUID id) {
        return jdbc.queryForObject("SELECT password_hash FROM administrators WHERE id = ?", String.class, id);
    }

    public void lockByUsername(String username) {
        jdbc.query(
                "SELECT id FROM administrators WHERE username = ? FOR UPDATE",
                (rs, row) -> rs.getObject("id"),
                username);
    }

    public boolean lockEnabled(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT enabled FROM administrators WHERE id = ? FOR UPDATE", Boolean.class, id));
    }

    public void auditRefreshReuse(UUID administratorId) {
        jdbc.update(
                "INSERT INTO administrator_audit(id, actor_administrator_id, target_administrator_id, action, outcome) "
                        + "VALUES (?, ?, ?, 'REFRESH_TOKEN_REUSE_DETECTED', 'DETECTED')",
                UUID.randomUUID(),
                administratorId,
                administratorId);
    }
}
