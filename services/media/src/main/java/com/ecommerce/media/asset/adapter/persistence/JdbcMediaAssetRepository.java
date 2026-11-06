package com.ecommerce.media.asset.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.media.asset.application.port.MediaAssetRepository;
import com.ecommerce.media.asset.domain.AssetStatus;
import com.ecommerce.media.asset.domain.MediaAsset;

@Repository
public class JdbcMediaAssetRepository implements MediaAssetRepository {
    private static final RowMapper<MediaAsset> MAPPER = JdbcMediaAssetRepository::map;
    private final JdbcTemplate jdbc;

    public JdbcMediaAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void createUploading(MediaAsset asset) {
        jdbc.update(
                "INSERT INTO media_assets(id, uploader_administrator_id, status, media_type, byte_size, width, height, "
                        + "provider_public_id, created_at, updated_at, version) VALUES (?, ?, 'UPLOADING', ?, ?, ?, ?, ?, ?, ?, 0)",
                asset.id(),
                asset.uploaderAdministratorId(),
                asset.mediaType(),
                asset.byteSize(),
                asset.width(),
                asset.height(),
                asset.providerPublicId(),
                timestamp(asset.createdAt()),
                timestamp(asset.createdAt()));
    }

    @Override
    @Transactional
    public boolean markReady(UUID id, String providerAssetId, Instant now) {
        return jdbc.update(
                        "UPDATE media_assets SET status = 'READY', provider_asset_id = ?, uploaded_at = ?, updated_at = ?, "
                                + "next_attempt_at = NULL, version = version + 1 WHERE id = ? AND status = 'UPLOADING'",
                        providerAssetId,
                        timestamp(now),
                        timestamp(now),
                        id)
                == 1;
    }

    @Override
    @Transactional
    public void markAmbiguousUpload(UUID id, String errorCategory, Instant retryAt, Instant now) {
        jdbc.update(
                "UPDATE media_assets SET status = 'AMBIGUOUS_UPLOAD', last_error_category = ?, next_attempt_at = ?, "
                        + "lease_id = NULL, lease_until = NULL, updated_at = ?, version = version + 1 "
                        + "WHERE id = ? AND status = 'UPLOADING' AND lease_id IS NULL",
                errorCategory,
                timestamp(retryAt),
                timestamp(now),
                id);
    }

    @Override
    public Optional<MediaAsset> find(UUID id) {
        return jdbc.query("SELECT * FROM media_assets WHERE id = ?", MAPPER, id).stream()
                .findFirst();
    }

    @Override
    @Transactional
    public boolean queueDeletion(UUID id, Instant now) {
        return jdbc.update(
                        "UPDATE media_assets SET status = 'DELETE_PENDING', delete_requested_at = ?, next_attempt_at = ?, "
                                + "updated_at = ?, version = version + 1 WHERE id = ? AND status = 'READY'",
                        timestamp(now),
                        timestamp(now),
                        timestamp(now),
                        id)
                == 1;
    }

    @Override
    @Transactional
    public List<MediaAsset> claimCleanup(Instant now, Instant abandonedBefore, Instant leaseUntil, int batchSize) {
        UUID leaseId = UUID.randomUUID();
        return jdbc.query(
                "WITH due AS (SELECT id FROM media_assets WHERE "
                        + "((status IN ('DELETE_PENDING', 'CLEANUP_PENDING', 'AMBIGUOUS_UPLOAD') AND "
                        + "((lease_until IS NULL AND next_attempt_at <= ?) OR lease_until <= ?)) "
                        + "OR (status = 'UPLOADING' AND created_at <= ? AND (lease_until IS NULL OR lease_until <= ?))) "
                        + "ORDER BY COALESCE(next_attempt_at, created_at), created_at "
                        + "LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE media_assets a SET status = CASE WHEN a.status = 'UPLOADING' THEN 'AMBIGUOUS_UPLOAD' ELSE a.status END, "
                        + "last_error_category = CASE WHEN a.status = 'UPLOADING' THEN 'UPLOAD_OUTCOME_UNKNOWN' ELSE a.last_error_category END, "
                        + "cleanup_attempts = cleanup_attempts + 1, next_attempt_at = NULL, lease_id = ?, lease_until = ?, "
                        + "updated_at = ?, version = version + 1 FROM due WHERE a.id = due.id RETURNING a.*",
                MAPPER,
                timestamp(now),
                timestamp(now),
                timestamp(abandonedBefore),
                timestamp(now),
                batchSize,
                leaseId,
                timestamp(leaseUntil),
                timestamp(now));
    }

    @Override
    @Transactional
    public void cleanupDeleted(UUID id, UUID leaseId, Instant now) {
        jdbc.update(
                "UPDATE media_assets SET status = 'DELETED', deleted_at = ?, next_attempt_at = NULL, lease_id = NULL, "
                        + "lease_until = NULL, last_error_category = NULL, updated_at = ?, version = version + 1 "
                        + "WHERE id = ? AND lease_id = ? AND status IN ('DELETE_PENDING', 'CLEANUP_PENDING', 'AMBIGUOUS_UPLOAD')",
                timestamp(now),
                timestamp(now),
                id,
                leaseId);
    }

    @Override
    @Transactional
    public void cleanupAmbiguousPending(UUID id, UUID leaseId, String category, Instant retryAt, Instant now) {
        jdbc.update(
                "UPDATE media_assets SET status = 'AMBIGUOUS_UPLOAD', last_error_category = ?, "
                        + "next_attempt_at = ?, lease_id = NULL, lease_until = NULL, updated_at = ?, version = version + 1 "
                        + "WHERE id = ? AND lease_id = ? AND status = 'AMBIGUOUS_UPLOAD'",
                category,
                timestamp(retryAt),
                timestamp(now),
                id,
                leaseId);
    }

    @Override
    @Transactional
    public void cleanupFailed(
            UUID id, UUID leaseId, boolean ambiguousUpload, String category, Instant retryAt, Instant now) {
        jdbc.update(
                "UPDATE media_assets SET status = CASE WHEN ? THEN 'AMBIGUOUS_UPLOAD' ELSE status END, "
                        + "last_error_category = ?, next_attempt_at = ?, lease_id = NULL, lease_until = NULL, updated_at = ?, "
                        + "version = version + 1 WHERE id = ? AND lease_id = ?",
                ambiguousUpload,
                category,
                timestamp(retryAt),
                timestamp(now),
                id,
                leaseId);
    }

    private static MediaAsset map(ResultSet rs, int row) throws SQLException {
        return new MediaAsset(
                rs.getObject("id", UUID.class),
                rs.getObject("uploader_administrator_id", UUID.class),
                AssetStatus.valueOf(rs.getString("status")),
                rs.getString("media_type"),
                rs.getLong("byte_size"),
                rs.getInt("width"),
                rs.getInt("height"),
                rs.getString("provider_public_id"),
                rs.getString("provider_asset_id"),
                instant(rs, "created_at"),
                instant(rs, "uploaded_at"),
                instant(rs, "delete_requested_at"),
                instant(rs, "deleted_at"),
                rs.getInt("cleanup_attempts"),
                instant(rs, "next_attempt_at"),
                rs.getObject("lease_id", UUID.class),
                instant(rs, "lease_until"),
                rs.getString("last_error_category"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
