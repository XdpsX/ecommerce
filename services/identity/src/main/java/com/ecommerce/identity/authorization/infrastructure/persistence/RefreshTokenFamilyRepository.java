package com.ecommerce.identity.authorization.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ecommerce.identity.admin.application.AdministratorDirectory;

@Repository
public class RefreshTokenFamilyRepository {
    private final JdbcTemplate jdbc;
    private final AdministratorDirectory administrators;

    public RefreshTokenFamilyRepository(JdbcTemplate jdbc, AdministratorDirectory administrators) {
        this.jdbc = jdbc;
        this.administrators = administrators;
    }

    public Optional<Family> findByTokenDigest(String digest) {
        return jdbc
                .query(
                        "SELECT f.family_id, f.authorization_id, f.administrator_id, f.registered_client_id, "
                                + "f.status, f.current_token_digest, f.current_expires_at "
                                + "FROM refresh_token_history h JOIN refresh_token_families f "
                                + "ON f.family_id = h.family_id WHERE h.token_digest = ?",
                        (rs, row) -> new Family(
                                rs.getObject("family_id", UUID.class),
                                rs.getString("authorization_id"),
                                rs.getObject("administrator_id", UUID.class),
                                rs.getString("registered_client_id"),
                                rs.getString("status"),
                                rs.getString("current_token_digest"),
                                rs.getTimestamp("current_expires_at").toInstant()),
                        digest)
                .stream()
                .findFirst();
    }

    public Optional<Family> lockFamily(UUID familyId) {
        return jdbc
                .query(
                        "SELECT family_id, authorization_id, administrator_id, registered_client_id, status, "
                                + "current_token_digest, current_expires_at FROM refresh_token_families "
                                + "WHERE family_id = ? FOR UPDATE",
                        (rs, row) -> new Family(
                                rs.getObject("family_id", UUID.class),
                                rs.getString("authorization_id"),
                                rs.getObject("administrator_id", UUID.class),
                                rs.getString("registered_client_id"),
                                rs.getString("status"),
                                rs.getString("current_token_digest"),
                                rs.getTimestamp("current_expires_at").toInstant()),
                        familyId)
                .stream()
                .findFirst();
    }

    public Optional<Family> findByAuthorizationId(String authorizationId) {
        return jdbc
                .query(
                        "SELECT family_id, authorization_id, administrator_id, registered_client_id, status, "
                                + "current_token_digest, current_expires_at FROM refresh_token_families "
                                + "WHERE authorization_id = ?",
                        (rs, row) -> new Family(
                                rs.getObject("family_id", UUID.class),
                                rs.getString("authorization_id"),
                                rs.getObject("administrator_id", UUID.class),
                                rs.getString("registered_client_id"),
                                rs.getString("status"),
                                rs.getString("current_token_digest"),
                                rs.getTimestamp("current_expires_at").toInstant()),
                        authorizationId)
                .stream()
                .findFirst();
    }

    public List<UUID> activeFamilyIdsForAdministrator(UUID administratorId) {
        return jdbc.query(
                "SELECT family_id FROM refresh_token_families WHERE administrator_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY family_id",
                (rs, row) -> rs.getObject(1, UUID.class),
                administratorId);
    }

    public boolean lockAdministrator(UUID administratorId) {
        return administrators.lockEnabled(administratorId);
    }

    public Optional<History> findHistory(String digest) {
        return jdbc
                .query(
                        "SELECT token_digest, family_id, issued_at, expires_at, consumed_at, successor_digest "
                                + "FROM refresh_token_history WHERE token_digest = ?",
                        (rs, row) -> new History(
                                rs.getString("token_digest"),
                                rs.getObject("family_id", UUID.class),
                                rs.getTimestamp("issued_at").toInstant(),
                                rs.getTimestamp("expires_at").toInstant(),
                                rs.getTimestamp("consumed_at") == null
                                        ? null
                                        : rs.getTimestamp("consumed_at").toInstant(),
                                rs.getString("successor_digest")),
                        digest)
                .stream()
                .findFirst();
    }

    public void rotate(
            UUID familyId, String previousDigest, String successorDigest, Instant issuedAt, Instant expiresAt) {
        int consumed = jdbc.update(
                "UPDATE refresh_token_history SET consumed_at = ?, successor_digest = ? "
                        + "WHERE token_digest = ? AND consumed_at IS NULL",
                Timestamp.from(issuedAt),
                successorDigest,
                previousDigest);
        if (consumed != 1) {
            throw new IllegalStateException("Refresh history changed while the family lock was held.");
        }
        jdbc.update(
                "INSERT INTO refresh_token_history(token_digest, family_id, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, ?)",
                successorDigest,
                familyId,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt));
        int updated = jdbc.update(
                "UPDATE refresh_token_families SET current_token_digest = ?, current_expires_at = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE family_id = ? AND status = 'ACTIVE'",
                successorDigest,
                Timestamp.from(expiresAt),
                familyId);
        if (updated != 1) {
            throw new IllegalStateException("Refresh family was not active during token rotation.");
        }
    }

    public void revoke(UUID familyId, Instant revokedAt, String reason) {
        int updated = jdbc.update(
                "UPDATE refresh_token_families SET status = 'REVOKED', revoked_at = ?, revoked_reason = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE family_id = ? AND status = 'ACTIVE'",
                Timestamp.from(revokedAt),
                reason,
                familyId);
        if (updated != 1) {
            throw new IllegalStateException("Refresh family was not active when replay revocation was applied.");
        }
    }

    public void revokeAllForAdministrator(UUID administratorId, Instant revokedAt, String reason) {
        for (UUID familyId : activeFamilyIdsForAdministrator(administratorId)) {
            Family family = lockFamily(familyId).orElse(null);
            if (family != null && "ACTIVE".equals(family.status())) {
                revoke(familyId, revokedAt, reason);
            }
        }
    }

    public void revokeForAuthorization(String authorizationId, Instant revokedAt, String reason) {
        Family initial = findByAuthorizationId(authorizationId).orElse(null);
        if (initial == null) {
            return;
        }
        lockAdministrator(initial.administratorId());
        Family family = lockFamily(initial.id()).orElse(null);
        if (family != null && "ACTIVE".equals(family.status())) {
            revoke(family.id(), revokedAt, reason);
        }
    }

    public void auditReuse(UUID administratorId) {
        administrators.auditRefreshReuse(administratorId);
    }

    public record Family(
            UUID id,
            String authorizationId,
            UUID administratorId,
            String registeredClientId,
            String status,
            String currentDigest,
            Instant expiresAt) {}

    public record History(
            String digest,
            UUID familyId,
            Instant issuedAt,
            Instant expiresAt,
            Instant consumedAt,
            String successorDigest) {}
}
