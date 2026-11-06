package com.ecommerce.media.asset.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.media.asset.application.exception.DirectUploadException;
import com.ecommerce.media.asset.application.exception.MediaPersistenceLockTimeoutException;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository;
import com.ecommerce.media.asset.domain.MediaAsset;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;

@Repository
public class JdbcMediaUploadIntentRepository implements MediaUploadIntentRepository {
    private static final long QUOTA_LOCK = 912_304_781L;
    private static final RowMapper<MediaUploadIntent> INTENT_MAPPER = JdbcMediaUploadIntentRepository::mapIntent;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independentTransaction;

    public JdbcMediaUploadIntentRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        independentTransaction = new TransactionTemplate(transactionManager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public void createBatch(List<MediaUploadIntent> intents, QuotaLimits limits, Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + QUOTA_LOCK + ")");
        Instant day = now.atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        UUID ownerId = intents.getFirst().ownerId();
        if (!issuanceEnabled()) throw new DirectUploadException(DirectUploadException.Kind.QUOTA_EXCEEDED, 60);
        long backlog = cleanupBacklog();
        Instant oldestDue = oldestRecoveryDueAt();
        if (backlog > limits.stopIssuanceBacklog()
                || oldestDue != null && oldestDue.isBefore(now.minus(limits.stopOldestRecovery()))) {
            independentTransaction.execute(status -> updateIssuanceEnabled(false, now));
            throw new DirectUploadException(DirectUploadException.Kind.QUOTA_EXCEEDED, 3_600);
        }

        reserveDaily("global", day, intents.size(), limits.globalDaily());
        reserveDaily("owner:" + ownerId, day, intents.size(), limits.perPrincipalDaily());
        int ownerActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_upload_intents WHERE owner_id = ? AND state = 'PENDING' AND expires_at > ?",
                Integer.class,
                ownerId,
                timestamp(now));
        int globalActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_upload_intents WHERE state = 'PENDING' AND expires_at > ?",
                Integer.class,
                timestamp(now));
        if (ownerActive + intents.size() > limits.perPrincipalActive()
                || globalActive + intents.size() > limits.globalActive()) {
            throw DirectUploadException.retry(java.time.Duration.ofSeconds(60));
        }
        for (MediaUploadIntent intent : intents) insertIntent(intent);
    }

    private void reserveDaily(String scope, Instant day, int count, int limit) {
        List<Integer> rows = jdbc.query(
                "INSERT INTO media_upload_quota_counters(scope_key, period_start, reserved_count) "
                        + "SELECT ?, ?, ? WHERE ? <= ? "
                        + "ON CONFLICT(scope_key, period_start) DO UPDATE SET reserved_count = "
                        + "media_upload_quota_counters.reserved_count + EXCLUDED.reserved_count "
                        + "WHERE media_upload_quota_counters.reserved_count + EXCLUDED.reserved_count <= ? "
                        + "RETURNING reserved_count",
                (rs, row) -> rs.getInt(1),
                scope,
                timestamp(day),
                count,
                count,
                limit,
                limit);
        if (rows.isEmpty()) throw DirectUploadException.retry(java.time.Duration.ofHours(1));
    }

    private void insertIntent(MediaUploadIntent i) {
        jdbc.update(
                "INSERT INTO media_upload_intents(id, owner_id, client_file_id, filename, declared_content_type, "
                        + "declared_size_bytes, policy_version, provider_public_id, state, created_at, signed_at, expires_at, "
                        + "next_attempt_at, discovery_due_at, discovery_resource_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'image')",
                i.id(),
                i.ownerId(),
                i.clientFileId(),
                i.filename(),
                i.declaredContentType(),
                i.declaredSizeBytes(),
                i.policyVersion(),
                i.providerPublicId(),
                i.state().name(),
                timestamp(i.createdAt()),
                timestamp(i.signedAt()),
                timestamp(i.expiresAt()),
                timestamp(i.nextAttemptAt()),
                timestamp(i.discoveryDueAt()));
    }

    @Override
    public Optional<MediaUploadIntent> find(UUID id) {
        return jdbc.query("SELECT * FROM media_upload_intents WHERE id = ?", INTENT_MAPPER, id).stream()
                .findFirst();
    }

    @Override
    public Optional<MediaUploadIntent> findOwned(UUID ownerId, UUID id) {
        return jdbc
                .query("SELECT * FROM media_upload_intents WHERE id = ? AND owner_id = ?", INTENT_MAPPER, id, ownerId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public boolean claimForCompletion(
            UUID id, UUID ownerId, UUID leaseToken, Instant now, Instant leaseUntil, Duration maxLockWait) {
        return translateLockTimeout(() -> {
            setLocalLockTimeout(maxLockWait);
            return jdbc.update(
                            "UPDATE media_upload_intents SET lease_token = ?, lease_until = ?, version = version + 1 "
                                    + "WHERE id = ? AND owner_id = ? AND state = 'PENDING' AND expires_at > ? "
                                    + "AND (lease_until IS NULL OR lease_until <= ?)",
                            leaseToken,
                            timestamp(leaseUntil),
                            id,
                            ownerId,
                            timestamp(now),
                            timestamp(now))
                    == 1;
        });
    }

    @Override
    @Transactional
    public List<MediaUploadIntent> claimDue(Instant now, UUID leaseToken, Instant leaseUntil, int limit) {
        return jdbc.query(
                "WITH due AS (SELECT id FROM media_upload_intents WHERE next_attempt_at <= ? "
                        + "AND (lease_until IS NULL OR lease_until <= ?) "
                        + "ORDER BY next_attempt_at, last_recovery_claim_at NULLS FIRST, created_at LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE media_upload_intents i SET lease_token = ?, lease_until = ?, last_recovery_claim_at = ?, version = version + 1 "
                        + "FROM due WHERE i.id = due.id RETURNING i.*",
                INTENT_MAPPER,
                timestamp(now),
                timestamp(now),
                limit,
                leaseToken,
                timestamp(leaseUntil),
                timestamp(now));
    }

    @Override
    @Transactional
    public boolean reserveProviderCall(Instant hourStart, boolean recovery, int hourlyLimit, int recoveryReserve) {
        return translateLockTimeout(() -> reserveProviderCallSql(hourStart, recovery, hourlyLimit, recoveryReserve));
    }

    @Override
    @Transactional
    public boolean reserveProviderCall(
            Instant hourStart, boolean recovery, int hourlyLimit, int recoveryReserve, Duration maxLockWait) {
        return translateLockTimeout(() -> {
            setLocalLockTimeout(maxLockWait);
            return reserveProviderCallSql(hourStart, recovery, hourlyLimit, recoveryReserve);
        });
    }

    private boolean reserveProviderCallSql(Instant hourStart, boolean recovery, int hourlyLimit, int recoveryReserve) {
        List<Integer> rows = jdbc.query(
                "INSERT INTO media_upload_provider_hourly_budget(hour_start, call_count, recovery_call_count) "
                        + "VALUES (?, 1, ?) ON CONFLICT(hour_start) DO UPDATE SET call_count = "
                        + "media_upload_provider_hourly_budget.call_count + 1, recovery_call_count = "
                        + "media_upload_provider_hourly_budget.recovery_call_count + EXCLUDED.recovery_call_count "
                        + "WHERE media_upload_provider_hourly_budget.call_count < ? AND "
                        + "(? OR media_upload_provider_hourly_budget.call_count - "
                        + "media_upload_provider_hourly_budget.recovery_call_count < ? - ?) RETURNING call_count",
                (rs, row) -> rs.getInt(1),
                timestamp(hourStart),
                recovery ? 1 : 0,
                hourlyLimit,
                recovery,
                hourlyLimit,
                recoveryReserve);
        return !rows.isEmpty();
    }

    private void setLocalLockTimeout(Duration maxLockWait) {
        long lockTimeoutMillis = Math.max(1, maxLockWait.toMillis());
        jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, lockTimeoutMillis + "ms");
    }

    @Override
    @Transactional
    public boolean completeAndCreateAsset(
            UUID intentId,
            UUID leaseToken,
            MediaAsset asset,
            TrustedProviderInspection inspection,
            Instant now,
            Duration maxLockWait) {
        return translateLockTimeout(() -> {
            setLocalLockTimeout(maxLockWait);
            List<MediaUploadIntent> locked =
                    jdbc.query("SELECT * FROM media_upload_intents WHERE id = ? FOR UPDATE", INTENT_MAPPER, intentId);
            if (locked.isEmpty()) return false;
            MediaUploadIntent intent = locked.getFirst();
            if (intent.state() != UploadIntentState.PENDING
                    || !leaseToken.equals(intent.leaseToken())
                    || !intent.expiresAt().isAfter(now)) return false;

            jdbc.update(
                    "INSERT INTO media_assets(id, uploader_administrator_id, status, media_type, byte_size, width, height, "
                            + "provider_public_id, provider_asset_id, created_at, updated_at, uploaded_at, version) "
                            + "VALUES (?, ?, 'READY', ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    asset.id(),
                    asset.uploaderAdministratorId(),
                    asset.mediaType(),
                    asset.byteSize(),
                    asset.width(),
                    asset.height(),
                    asset.providerPublicId(),
                    asset.providerAssetId(),
                    timestamp(now),
                    timestamp(now),
                    timestamp(now));
            int linked = jdbc.update(
                    "UPDATE media_upload_intents SET state = 'COMPLETED', media_id = ?, source_bytes = ?, "
                            + "source_width = ?, source_height = ?, source_format = ?, source_evidence = ?, "
                            + "provider_asset_id = ?, final_bytes = ?, final_width = ?, final_height = ?, final_format = ?, "
                            + "final_resource_type = ?, final_delivery_type = ?, result_status = 'READY', error_code = NULL, "
                            + "next_attempt_at = ?, lease_token = NULL, lease_until = NULL, version = version + 1 "
                            + "WHERE id = ? AND state = 'PENDING' AND lease_token = ? AND expires_at > ?",
                    asset.id(),
                    inspection.sourceBytes(),
                    inspection.sourceWidth(),
                    inspection.sourceHeight(),
                    inspection.sourceFormat(),
                    inspection.sourceEvidence(),
                    inspection.assetId(),
                    inspection.finalBytes(),
                    inspection.finalWidth(),
                    inspection.finalHeight(),
                    inspection.format(),
                    inspection.resourceType(),
                    inspection.deliveryType(),
                    timestamp(now.plusSeconds(86_400)),
                    intentId,
                    leaseToken,
                    timestamp(now));
            if (linked != 1) throw new IllegalStateException("The upload intent changed during finalization.");
            return true;
        });
    }

    @Override
    @Transactional
    public boolean reject(
            UUID intentId,
            UUID leaseToken,
            String errorCode,
            Instant nextDiscoveryAt,
            Instant now,
            Duration maxLockWait) {
        return translateLockTimeout(() -> {
            setLocalLockTimeout(maxLockWait);
            return jdbc.update(
                            "UPDATE media_upload_intents SET state = 'REJECTED', result_status = 'REJECTED', error_code = ?, "
                                    + "next_attempt_at = ?, discovery_due_at = ?, lease_token = NULL, lease_until = NULL, "
                                    + "version = version + 1 WHERE id = ? AND state = 'PENDING' AND lease_token = ?",
                            errorCode,
                            timestamp(nextDiscoveryAt),
                            timestamp(nextDiscoveryAt),
                            intentId,
                            leaseToken)
                    == 1;
        });
    }

    @Override
    @Transactional
    public boolean expire(UUID intentId, UUID leaseToken, Instant nextDiscoveryAt, Instant now) {
        return jdbc.update(
                        "UPDATE media_upload_intents SET state = 'EXPIRED', result_status = 'EXPIRED', error_code = NULL, "
                                + "next_attempt_at = ?, discovery_due_at = ?, lease_token = NULL, lease_until = NULL, "
                                + "version = version + 1 WHERE id = ? AND state = 'PENDING' AND lease_token = ? AND expires_at <= ?",
                        timestamp(nextDiscoveryAt),
                        timestamp(nextDiscoveryAt),
                        intentId,
                        leaseToken,
                        timestamp(now))
                == 1;
    }

    @Override
    @Transactional
    public boolean retry(
            UUID intentId, UUID leaseToken, String category, Instant nextAttemptAt, Instant now, Duration maxLockWait) {
        return translateLockTimeout(() -> {
            setLocalLockTimeout(maxLockWait);
            return jdbc.update(
                            "UPDATE media_upload_intents SET retry_count = retry_count + 1, error_code = 'RETRYABLE', last_error_category = ?, "
                                    + "result_status = 'PENDING', next_attempt_at = ?, lease_token = NULL, lease_until = NULL, "
                                    + "version = version + 1 WHERE id = ? AND lease_token = ?",
                            safeCategory(category),
                            timestamp(nextAttemptAt),
                            intentId,
                            leaseToken)
                    == 1;
        });
    }

    @Override
    @Transactional
    public boolean scheduleInspection(String publicId, Instant now) {
        return jdbc.update(
                        "UPDATE media_upload_intents SET next_attempt_at = ?, error_code = NULL, version = version + 1 "
                                + "WHERE provider_public_id = ? AND state = 'PENDING'",
                        timestamp(now),
                        publicId)
                == 1;
    }

    @Override
    @Transactional
    public boolean saveDiscoveryProgress(
            UUID intentId, UUID leaseToken, String resourceType, String cursor, Instant nextDueAt, Instant now) {
        return jdbc.update(
                        "UPDATE media_upload_intents SET discovery_resource_type = ?, discovery_cursor = ?, "
                                + "discovery_due_at = ?, next_attempt_at = CASE WHEN EXISTS (SELECT 1 "
                                + "FROM media_upload_discovered_objects d WHERE d.intent_id = media_upload_intents.id "
                                + "AND d.state <> 'DELETED' AND d.next_attempt_at <= CAST(? AS TIMESTAMPTZ)) "
                                + "THEN CAST(? AS TIMESTAMPTZ) ELSE CAST(? AS TIMESTAMPTZ) END, "
                                + "lease_token = NULL, lease_until = NULL, "
                                + "version = version + 1 WHERE id = ? AND lease_token = ?",
                        resourceType,
                        cursor,
                        timestamp(nextDueAt),
                        timestamp(now),
                        timestamp(now),
                        timestamp(nextDueAt),
                        intentId,
                        leaseToken)
                == 1;
    }

    @Override
    @Transactional
    public void saveDiscoveredTargets(UUID intentId, List<DirectTarget> targets, Instant now) {
        for (DirectTarget target : targets) {
            jdbc.update(
                    "INSERT INTO media_upload_discovered_objects(intent_id, resource_type, delivery_type, public_id, asset_id, state, "
                            + "next_attempt_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'DISCOVERED', ?, ?, ?) "
                            + "ON CONFLICT(intent_id, resource_type, public_id) DO UPDATE SET "
                            + "state = CASE WHEN media_upload_discovered_objects.asset_id IS DISTINCT FROM EXCLUDED.asset_id "
                            + "THEN 'DISCOVERED' ELSE media_upload_discovered_objects.state END, "
                            + "lease_token = CASE WHEN media_upload_discovered_objects.asset_id IS DISTINCT FROM EXCLUDED.asset_id "
                            + "THEN NULL ELSE media_upload_discovered_objects.lease_token END, "
                            + "lease_until = CASE WHEN media_upload_discovered_objects.asset_id IS DISTINCT FROM EXCLUDED.asset_id "
                            + "THEN NULL ELSE media_upload_discovered_objects.lease_until END, "
                            + "next_attempt_at = CASE WHEN media_upload_discovered_objects.asset_id IS DISTINCT FROM EXCLUDED.asset_id "
                            + "THEN CASE WHEN media_upload_discovered_objects.state = 'DELETE_PENDING' "
                            + "AND media_upload_discovered_objects.lease_until > EXCLUDED.next_attempt_at "
                            + "THEN media_upload_discovered_objects.lease_until ELSE EXCLUDED.next_attempt_at END "
                            + "ELSE media_upload_discovered_objects.next_attempt_at END, "
                            + "last_error_category = CASE WHEN media_upload_discovered_objects.asset_id IS DISTINCT FROM EXCLUDED.asset_id "
                            + "THEN NULL ELSE media_upload_discovered_objects.last_error_category END, "
                            + "asset_id = EXCLUDED.asset_id, "
                            + "updated_at = EXCLUDED.updated_at",
                    intentId,
                    target.resourceType(),
                    target.deliveryType(),
                    target.publicId(),
                    target.assetId(),
                    timestamp(now),
                    timestamp(now),
                    timestamp(now));
        }
    }

    @Override
    @Transactional
    public List<DiscoveredTarget> claimDiscoveredTargets(
            UUID intentId, UUID leaseToken, Instant now, Instant leaseUntil, int limit) {
        return jdbc.query(
                "WITH due AS (SELECT id FROM media_upload_discovered_objects WHERE intent_id = ? AND "
                        + "((state IN ('DISCOVERED', 'RETRY') AND next_attempt_at <= ?) "
                        + "OR (state = 'DELETE_PENDING' AND lease_until <= ? AND next_attempt_at <= ?)) "
                        + "ORDER BY next_attempt_at, id LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE media_upload_discovered_objects d SET state = 'DELETE_PENDING', lease_token = ?, "
                        + "lease_until = ?, attempts = attempts + 1, updated_at = ? FROM due WHERE d.id = due.id "
                        + "RETURNING d.id, d.intent_id, d.resource_type, d.delivery_type, d.public_id, d.asset_id, d.attempts, d.lease_token",
                (rs, row) -> new DiscoveredTarget(
                        rs.getLong("id"),
                        rs.getObject("intent_id", UUID.class),
                        rs.getString("resource_type"),
                        rs.getString("delivery_type"),
                        rs.getString("public_id"),
                        rs.getString("asset_id"),
                        rs.getInt("attempts"),
                        rs.getObject("lease_token", UUID.class)),
                intentId,
                timestamp(now),
                timestamp(now),
                timestamp(now),
                limit,
                leaseToken,
                timestamp(leaseUntil),
                timestamp(now));
    }

    @Override
    @Transactional
    public boolean markTargetDeleted(long targetId, UUID leaseToken, Instant now) {
        return jdbc.update(
                        "UPDATE media_upload_discovered_objects SET state = 'DELETED', last_error_category = NULL, "
                                + "lease_token = NULL, lease_until = NULL, updated_at = ? "
                                + "WHERE id = ? AND state = 'DELETE_PENDING' AND lease_token = ?",
                        timestamp(now),
                        targetId,
                        leaseToken)
                == 1;
    }

    @Override
    @Transactional
    public boolean retryTarget(long targetId, UUID leaseToken, String category, Instant nextAttemptAt, Instant now) {
        int updated = jdbc.update(
                "UPDATE media_upload_discovered_objects SET state = 'RETRY', last_error_category = ?, "
                        + "next_attempt_at = ?, lease_token = NULL, lease_until = NULL, updated_at = ? "
                        + "WHERE id = ? AND state = 'DELETE_PENDING' AND lease_token = ?",
                category,
                timestamp(nextAttemptAt),
                timestamp(now),
                targetId,
                leaseToken);
        if (updated == 1) {
            jdbc.update(
                    "UPDATE media_upload_intents SET next_attempt_at = LEAST(next_attempt_at, ?), version = version + 1 "
                            + "WHERE id = (SELECT intent_id FROM media_upload_discovered_objects WHERE id = ?)",
                    timestamp(nextAttemptAt),
                    targetId);
        }
        return updated == 1;
    }

    @Override
    @Transactional
    public boolean setIssuanceEnabled(boolean enabled, Instant now) {
        return updateIssuanceEnabled(enabled, now);
    }

    private boolean updateIssuanceEnabled(boolean enabled, Instant now) {
        return jdbc.update(
                        "INSERT INTO media_upload_controls(control_key, control_value, updated_at) VALUES ('issuance_enabled', ?, ?) "
                                + "ON CONFLICT(control_key) DO UPDATE SET control_value = EXCLUDED.control_value, updated_at = EXCLUDED.updated_at",
                        Boolean.toString(enabled),
                        timestamp(now))
                > 0;
    }

    @Override
    public boolean issuanceEnabled() {
        try {
            return Boolean.parseBoolean(jdbc.queryForObject(
                    "SELECT control_value FROM media_upload_controls WHERE control_key = 'issuance_enabled'",
                    String.class));
        } catch (EmptyResultDataAccessException exception) {
            return true;
        }
    }

    @Override
    public long cleanupBacklog() {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT i.id) FROM media_upload_intents i WHERE "
                        + "(i.state IN ('REJECTED', 'EXPIRED') OR (i.state = 'PENDING' AND i.expires_at <= CURRENT_TIMESTAMP)) "
                        + "AND i.next_attempt_at <= CURRENT_TIMESTAMP "
                        + "OR EXISTS (SELECT 1 FROM media_upload_discovered_objects d WHERE d.intent_id = i.id "
                        + "AND d.state NOT IN ('DELETED') AND d.next_attempt_at <= CURRENT_TIMESTAMP)",
                Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public Instant oldestRecoveryDueAt() {
        Timestamp value = jdbc.queryForObject(
                "SELECT MIN(next_attempt_at) FROM media_upload_intents WHERE next_attempt_at <= CURRENT_TIMESTAMP",
                Timestamp.class);
        return value == null ? null : value.toInstant();
    }

    private static MediaUploadIntent mapIntent(ResultSet rs, int row) throws SQLException {
        return new MediaUploadIntent(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("client_file_id"),
                rs.getString("filename"),
                rs.getString("declared_content_type"),
                rs.getLong("declared_size_bytes"),
                rs.getString("policy_version"),
                rs.getString("provider_public_id"),
                UploadIntentState.valueOf(rs.getString("state")),
                instant(rs, "created_at"),
                instant(rs, "signed_at"),
                instant(rs, "expires_at"),
                rs.getObject("media_id", UUID.class),
                nullableLong(rs, "source_bytes"),
                nullableInt(rs, "source_width"),
                nullableInt(rs, "source_height"),
                rs.getString("source_format"),
                rs.getString("source_evidence"),
                rs.getString("provider_asset_id"),
                nullableLong(rs, "final_bytes"),
                nullableInt(rs, "final_width"),
                nullableInt(rs, "final_height"),
                rs.getString("final_format"),
                rs.getString("final_resource_type"),
                rs.getString("final_delivery_type"),
                rs.getString("result_status"),
                rs.getString("error_code"),
                rs.getInt("retry_count"),
                instant(rs, "next_attempt_at"),
                instant(rs, "discovery_due_at"),
                rs.getString("discovery_cursor"),
                rs.getString("discovery_resource_type"),
                rs.getObject("lease_token", UUID.class),
                instant(rs, "lease_until"),
                rs.getLong("version"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static String safeCategory(String category) {
        if (category == null) return "OPERATION_FAILED";
        return category.matches("[A-Z0-9_]{1,64}") ? category : "OPERATION_FAILED";
    }

    private static <T> T translateLockTimeout(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            for (Throwable current = exception; current != null; current = current.getCause()) {
                if (current instanceof SQLException sqlException && "55P03".equals(sqlException.getSQLState())) {
                    throw new MediaPersistenceLockTimeoutException(exception);
                }
            }
            throw exception;
        }
    }
}
