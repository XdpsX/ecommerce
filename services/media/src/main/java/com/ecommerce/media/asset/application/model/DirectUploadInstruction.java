package com.ecommerce.media.asset.application.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DirectUploadInstruction(
        String clientFileId, UUID intentId, String uploadUrl, Map<String, String> fields, Instant expiresAt) {}
