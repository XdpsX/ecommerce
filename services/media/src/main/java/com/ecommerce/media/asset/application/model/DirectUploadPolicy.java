package com.ecommerce.media.asset.application.model;

import java.time.Instant;
import java.util.UUID;

public record DirectUploadPolicy(
        UUID intentId,
        String publicId,
        String policyVersion,
        long maxBytes,
        int maxSide,
        long maxPixels,
        Instant issuedAt,
        Instant expiresAt,
        String incomingTransformation,
        String notificationUrl) {}
