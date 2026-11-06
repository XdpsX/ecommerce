package com.ecommerce.media.asset.application.port;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.media.asset.application.model.CanonicalImage;

public interface MediaStorage {
    String allocateObjectKey(UUID mediaId);

    StoredAsset upload(String publicId, CanonicalImage image);

    StoredAsset inspect(String publicId);

    String downloadLink(String publicId, String format, Instant expiresAt);

    DeleteResult delete(String publicId);

    record StoredAsset(String assetId, String publicId, String resourceType, String deliveryType, String format) {}

    enum DeleteResult {
        DELETED,
        NOT_FOUND
    }
}
