package com.ecommerce.api.category.application.model;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        UUID imageAssetId,
        String status,
        String visibility,
        int displayOrder,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        long structureRevision,
        boolean effectivelyVisible) {}
