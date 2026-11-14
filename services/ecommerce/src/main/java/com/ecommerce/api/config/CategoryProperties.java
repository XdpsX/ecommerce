package com.ecommerce.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("category")
public record CategoryProperties(int maxDepth) {
    public CategoryProperties {
        if (maxDepth < 1 || maxDepth > 64) throw new IllegalArgumentException("category.max-depth must be 1..64");
    }
}
