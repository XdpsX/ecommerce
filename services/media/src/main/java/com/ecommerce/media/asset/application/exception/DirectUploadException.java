package com.ecommerce.media.asset.application.exception;

import java.time.Duration;

public final class DirectUploadException extends RuntimeException {
    private final Kind kind;
    private final long retryAfterSeconds;

    public DirectUploadException(Kind kind) {
        this(kind, 0);
    }

    public DirectUploadException(Kind kind, long retryAfterSeconds) {
        super(kind.code);
        this.kind = kind;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return kind.code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Kind {
        BAD_REQUEST("INVALID_REQUEST"),
        QUOTA_EXCEEDED("UPLOAD_QUOTA_EXCEEDED"),
        DISABLED("DIRECT_UPLOAD_UNAVAILABLE"),
        PROVIDER_UNAVAILABLE("MEDIA_PROVIDER_UNAVAILABLE");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static DirectUploadException retry(Duration duration) {
        return new DirectUploadException(Kind.QUOTA_EXCEEDED, Math.max(1, duration.toSeconds()));
    }
}
