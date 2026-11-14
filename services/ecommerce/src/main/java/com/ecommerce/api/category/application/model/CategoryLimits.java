package com.ecommerce.api.category.application.model;

public record CategoryLimits(int maxDepth) {
    public CategoryLimits {
        if (maxDepth < 1 || maxDepth > 64) throw new IllegalArgumentException("Category max depth must be 1..64.");
    }
}
