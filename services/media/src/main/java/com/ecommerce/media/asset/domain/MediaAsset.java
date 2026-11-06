package com.ecommerce.media.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record MediaAsset(
        UUID id,
        UUID uploaderAdministratorId,
        AssetStatus status,
        String mediaType,
        long byteSize,
        int width,
        int height,
        String providerPublicId,
        String providerAssetId,
        Instant createdAt,
        Instant uploadedAt,
        Instant deleteRequestedAt,
        Instant deletedAt,
        int cleanupAttempts,
        Instant nextAttemptAt,
        UUID leaseId,
        Instant leaseUntil,
        String lastErrorCategory,
        long version) {}
