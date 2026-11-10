package com.ecommerce.media.asset.application.model;

import java.util.UUID;

public record UploadIntentOutcome(UUID intentId, Status status, UUID mediaId, ErrorCode errorCode) {
    public enum Status {
        PENDING,
        READY,
        REJECTED,
        EXPIRED,
        DELETED,
        UNAVAILABLE,
        NOT_FOUND
    }

    public enum ErrorCode {
        RETRYABLE,
        INPUT_POLICY_REJECTED,
        OUTPUT_LIMIT_EXCEEDED,
        PROVIDER_IDENTITY_MISMATCH,
        UNSUPPORTED_INPUT
    }
}
