package com.ecommerce.media.asset.application.model;

import java.util.UUID;

public record TrustedProviderInspection(
        String assetId,
        String publicId,
        String resourceType,
        String deliveryType,
        String format,
        Long finalBytes,
        Integer finalWidth,
        Integer finalHeight,
        String policyVersion,
        UUID intentId,
        Long sourceBytes,
        Integer sourceWidth,
        Integer sourceHeight,
        String sourceFormat,
        String sourceEvidence) {}
