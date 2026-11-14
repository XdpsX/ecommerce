package com.ecommerce.api.category.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CategoryData(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        UUID imageAssetId,
        String status,
        String visibility,
        int displayOrder,
        Long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy) {

    public static CategoryData create(
            UUID id,
            UUID parent,
            String name,
            String slug,
            String description,
            UUID imageId,
            String status,
            String visibility,
            int order,
            Instant now,
            UUID actor) {
        return new CategoryData(
                id, parent, name, slug, description, imageId, status, visibility, order, null, now, actor, now, actor);
    }

    public CategoryData update(
            String name,
            String slug,
            String description,
            UUID imageId,
            String status,
            String visibility,
            Instant now,
            UUID actor) {
        if (this.name.equals(name)
                && this.slug.equals(slug)
                && Objects.equals(this.description, description)
                && Objects.equals(imageAssetId, imageId)
                && this.status.equals(status)
                && this.visibility.equals(visibility)) return this;
        return new CategoryData(
                id,
                parentId,
                name,
                slug,
                description,
                imageId,
                status,
                visibility,
                displayOrder,
                version,
                createdAt,
                createdBy,
                now,
                actor);
    }

    public CategoryData move(UUID parent, int order, Instant now, UUID actor) {
        if (Objects.equals(parentId, parent) && displayOrder == order) return this;
        return new CategoryData(
                id,
                parent,
                name,
                slug,
                description,
                imageAssetId,
                status,
                visibility,
                order,
                version,
                createdAt,
                createdBy,
                now,
                actor);
    }
}
