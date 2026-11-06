package com.ecommerce.media.asset.application.model;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.media.asset.domain.MediaAsset;

public record MediaAssetView(
        UUID id,
        String status,
        String mediaType,
        long byteSize,
        int width,
        int height,
        UUID uploadedBy,
        Instant createdAt,
        Instant uploadedAt) {
    public static MediaAssetView from(MediaAsset asset) {
        return new MediaAssetView(
                asset.id(),
                asset.status().name(),
                asset.mediaType(),
                asset.byteSize(),
                asset.width(),
                asset.height(),
                asset.uploaderAdministratorId(),
                asset.createdAt(),
                asset.uploadedAt());
    }
}
