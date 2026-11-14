package com.ecommerce.api.category.adapter.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    UUID id;

    @Column(name = "parent_id")
    UUID parentId;

    @Column(nullable = false, length = 200)
    String name;

    @Column(nullable = false, length = 200, unique = true)
    String slug;

    @Column(length = 4000)
    String description;

    @Column(name = "image_asset_id")
    UUID imageAssetId;

    @Column(nullable = false, length = 16)
    String status;

    @Column(nullable = false, length = 16)
    String visibility;

    @Column(name = "display_order", nullable = false)
    int displayOrder;

    @Version
    @Column(nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    UUID updatedBy;

    protected CategoryEntity() {}

    public static CategoryEntity create(
            UUID id,
            UUID parentId,
            String name,
            String slug,
            String description,
            UUID imageAssetId,
            String status,
            String visibility,
            int displayOrder,
            Instant now,
            UUID actor) {
        CategoryEntity entity = new CategoryEntity();
        entity.id = id;
        entity.parentId = parentId;
        entity.name = name;
        entity.slug = slug;
        entity.description = description;
        entity.imageAssetId = imageAssetId;
        entity.status = status;
        entity.visibility = visibility;
        entity.displayOrder = displayOrder;
        entity.createdAt = now;
        entity.createdBy = actor;
        entity.updatedAt = now;
        entity.updatedBy = actor;
        return entity;
    }

    public void update(
            String name,
            String slug,
            String description,
            UUID imageAssetId,
            String status,
            String visibility,
            Instant now,
            UUID actor) {
        boolean changed = !this.name.equals(name)
                || !this.slug.equals(slug)
                || !java.util.Objects.equals(this.description, description)
                || !java.util.Objects.equals(this.imageAssetId, imageAssetId)
                || !this.status.equals(status)
                || !this.visibility.equals(visibility);
        if (!changed) return;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageAssetId = imageAssetId;
        this.status = status;
        this.visibility = visibility;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void move(UUID parentId, int displayOrder, Instant now, UUID actor) {
        if (java.util.Objects.equals(this.parentId, parentId) && this.displayOrder == displayOrder) return;
        this.parentId = parentId;
        this.displayOrder = displayOrder;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID id() {
        return id;
    }

    public UUID parentId() {
        return parentId;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public String description() {
        return description;
    }

    public UUID imageAssetId() {
        return imageAssetId;
    }

    public String status() {
        return status;
    }

    public String visibility() {
        return visibility;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public Long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID updatedBy() {
        return updatedBy;
    }
}
