package com.ecommerce.api.category.application.model;

import java.util.UUID;

public record CategoryFilter(
        String term, String status, String visibility, UUID parentId, boolean rootsOnly, UUID rootId) {}
