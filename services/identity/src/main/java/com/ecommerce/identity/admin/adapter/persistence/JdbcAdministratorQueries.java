package com.ecommerce.identity.admin.adapter.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ecommerce.identity.admin.application.port.AdministratorQueries;
import com.ecommerce.identity.admin.domain.Permission;

@Repository
public class JdbcAdministratorQueries implements AdministratorQueries {
    private final JdbcTemplate jdbc;

    public JdbcAdministratorQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page list(int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM administrators", Long.class);
        List<AdministratorView> items = jdbc.query(
                "SELECT id, username, enabled FROM administrators ORDER BY username, id LIMIT ? OFFSET ?",
                (rs, row) -> new AdministratorView(
                        rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("enabled"), Set.of()),
                size,
                (long) page * size);
        return new Page(items, page, size, total);
    }

    public AdministratorView find(UUID id) {
        List<AdministratorView> result = jdbc.query(
                "SELECT id, username, enabled FROM administrators WHERE id = ?",
                (rs, row) -> new AdministratorView(
                        rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("enabled"), Set.of()),
                id);
        if (result.isEmpty()) {
            return null;
        }
        Set<String> roles = Set.copyOf(jdbc.query(
                "SELECT r.code FROM roles r JOIN administrator_roles ar ON ar.role_id = r.id "
                        + "WHERE ar.administrator_id = ? ORDER BY r.code",
                (rs, row) -> rs.getString(1),
                id));
        AdministratorView row = result.getFirst();
        return new AdministratorView(row.id(), row.username(), row.enabled(), roles);
    }

    public List<String> roles() {
        return jdbc.query("SELECT code FROM roles ORDER BY code", (rs, row) -> rs.getString(1));
    }

    public List<Permission> permissions() {
        return jdbc.query(
                "SELECT code, audience FROM permissions ORDER BY audience, code",
                (rs, row) -> new Permission(rs.getString("code"), rs.getString("audience")));
    }
}
