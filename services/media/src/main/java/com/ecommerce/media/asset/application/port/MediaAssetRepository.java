package com.ecommerce.media.asset.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.media.asset.domain.MediaAsset;

public interface MediaAssetRepository {
    void createUploading(MediaAsset asset);

    boolean markReady(UUID id, String providerAssetId, Instant now);

    void markAmbiguousUpload(UUID id, String errorCategory, Instant retryAt, Instant now);

    Optional<MediaAsset> find(UUID id);

    boolean queueDeletion(UUID id, Instant now);

    List<MediaAsset> claimCleanup(Instant now, Instant abandonedBefore, Instant leaseUntil, int batchSize);

    void cleanupDeleted(UUID id, UUID leaseId, Instant now);

    void cleanupAmbiguousPending(UUID id, UUID leaseId, String category, Instant retryAt, Instant now);

    void cleanupFailed(UUID id, UUID leaseId, boolean ambiguousUpload, String category, Instant retryAt, Instant now);
}
