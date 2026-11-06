package com.ecommerce.media.asset.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.domain.MediaAsset;
import com.ecommerce.media.asset.domain.MediaUploadIntent;

public interface MediaUploadIntentRepository {
    record QuotaLimits(
            int perPrincipalDaily,
            int globalDaily,
            int perPrincipalActive,
            int globalActive,
            int stopIssuanceBacklog,
            Duration stopOldestRecovery) {}

    record DiscoveredTarget(
            long id,
            UUID intentId,
            String resourceType,
            String deliveryType,
            String publicId,
            String assetId,
            int attempts,
            UUID leaseToken) {}

    void createBatch(List<MediaUploadIntent> intents, QuotaLimits limits, Instant now);

    Optional<MediaUploadIntent> find(UUID id);

    Optional<MediaUploadIntent> findOwned(UUID ownerId, UUID id);

    boolean claimForCompletion(
            UUID id, UUID ownerId, UUID leaseToken, Instant now, Instant leaseUntil, Duration maxLockWait);

    List<MediaUploadIntent> claimDue(Instant now, UUID leaseToken, Instant leaseUntil, int limit);

    boolean reserveProviderCall(Instant hourStart, boolean recovery, int hourlyLimit, int recoveryReserve);

    boolean reserveProviderCall(
            Instant hourStart, boolean recovery, int hourlyLimit, int recoveryReserve, Duration maxLockWait);

    boolean completeAndCreateAsset(
            UUID intentId,
            UUID leaseToken,
            MediaAsset readyAsset,
            TrustedProviderInspection inspection,
            Instant now,
            Duration maxLockWait);

    boolean reject(
            UUID intentId,
            UUID leaseToken,
            String errorCode,
            Instant nextDiscoveryAt,
            Instant now,
            Duration maxLockWait);

    boolean expire(UUID intentId, UUID leaseToken, Instant nextDiscoveryAt, Instant now);

    boolean retry(
            UUID intentId,
            UUID leaseToken,
            String errorCategory,
            Instant nextAttemptAt,
            Instant now,
            Duration maxLockWait);

    boolean scheduleInspection(String publicId, Instant now);

    boolean saveDiscoveryProgress(
            UUID intentId, UUID leaseToken, String resourceType, String cursor, Instant nextDueAt, Instant now);

    void saveDiscoveredTargets(UUID intentId, List<DirectTarget> targets, Instant now);

    List<DiscoveredTarget> claimDiscoveredTargets(
            UUID intentId, UUID leaseToken, Instant now, Instant leaseUntil, int limit);

    boolean markTargetDeleted(long targetId, UUID leaseToken, Instant now);

    boolean retryTarget(long targetId, UUID leaseToken, String category, Instant nextAttemptAt, Instant now);

    boolean setIssuanceEnabled(boolean enabled, Instant now);

    boolean issuanceEnabled();

    long cleanupBacklog();

    Instant oldestRecoveryDueAt();

    record DirectTarget(String resourceType, String deliveryType, String publicId, String assetId) {}
}
