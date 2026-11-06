package com.ecommerce.identity.admin.adapter.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ecommerce.identity.admin.application.exception.AdministratorException;
import com.ecommerce.identity.admin.application.port.BootstrapAdministratorStore;

@Repository
public class JdbcBootstrapAdministratorStore implements BootstrapAdministratorStore {
    private final JdbcTemplate jdbc;

    public JdbcBootstrapAdministratorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Marker lockMarker() {
        Integer singleton = jdbc.queryForObject(
                "SELECT singleton_id FROM identity_security_state WHERE singleton_id = 1 FOR UPDATE", Integer.class);
        if (singleton == null) {
            throw new AdministratorException("Identity security state is not initialized.");
        }
        Marker marker = jdbc.queryForObject(
                "SELECT bootstrap_completed_at IS NOT NULL AS completed, bootstrap_administrator_id "
                        + "FROM identity_security_state WHERE singleton_id = 1",
                (rs, row) ->
                        new Marker(rs.getBoolean("completed"), rs.getObject("bootstrap_administrator_id", UUID.class)));
        return marker;
    }

    public int administratorCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM administrators", Integer.class);
    }

    public void create(UUID administratorId, String username, String passwordHash) {
        jdbc.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                administratorId,
                username,
                passwordHash);
        jdbc.update(
                "INSERT INTO administrator_roles(administrator_id, role_id) "
                        + "SELECT ?, id FROM roles WHERE code = 'SUPER_ADMIN'",
                administratorId);
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_roles ar JOIN roles r ON r.id = ar.role_id "
                                + "WHERE ar.administrator_id = ? AND r.code = 'SUPER_ADMIN'",
                        Integer.class,
                        administratorId)
                != 1) {
            throw new AdministratorException("SUPER_ADMIN role is not initialized.");
        }
        jdbc.update(
                "UPDATE identity_security_state SET bootstrap_completed_at = CURRENT_TIMESTAMP, "
                        + "bootstrap_administrator_id = ? WHERE singleton_id = 1 AND bootstrap_completed_at IS NULL",
                administratorId);
        jdbc.update(
                "INSERT INTO administrator_audit(id, target_administrator_id, action, outcome) "
                        + "VALUES (?, ?, 'BOOTSTRAP_ADMINISTRATOR_CREATED', 'SUCCESS')",
                UUID.randomUUID(),
                administratorId);
    }
}
