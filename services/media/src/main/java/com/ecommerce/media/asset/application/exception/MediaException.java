package com.ecommerce.media.asset.application.exception;

public final class MediaException extends RuntimeException {
    private final Kind kind;

    public MediaException(Kind kind) {
        this(kind, kind.detail());
    }

    private MediaException(Kind kind, String safeDetail) {
        super(safeDetail);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return kind.code();
    }

    public static MediaException invalidImage(String detail) {
        return new MediaException(Kind.INVALID_IMAGE, detail);
    }

    public static MediaException tooLarge() {
        return new MediaException(Kind.FILE_TOO_LARGE);
    }

    public static MediaException unsupported() {
        return new MediaException(Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    public static MediaException conflict() {
        return new MediaException(Kind.STATE_CONFLICT);
    }

    public static MediaException unavailable() {
        return new MediaException(Kind.STORAGE_UNAVAILABLE);
    }

    public static MediaException processingBusy() {
        return new MediaException(Kind.PROCESSING_BUSY);
    }

    public static MediaException notFound() {
        return new MediaException(Kind.NOT_FOUND);
    }

    public enum Kind {
        INVALID_IMAGE("INVALID_MEDIA_IMAGE", "The image could not be safely decoded."),
        FILE_TOO_LARGE("FILE_TOO_LARGE", "The image exceeds the configured size limit."),
        UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Only JPEG and PNG images are accepted."),
        STATE_CONFLICT("MEDIA_STATE_CONFLICT", "The media asset is not available in its current state."),
        STORAGE_UNAVAILABLE("MEDIA_PROVIDER_UNAVAILABLE", "Media storage is temporarily unavailable."),
        PROCESSING_BUSY("MEDIA_PROCESSING_BUSY", "Image processing capacity is temporarily busy."),
        NOT_FOUND("MEDIA_NOT_FOUND", "The media asset was not found.");

        private final String code;
        private final String detail;

        Kind(String code, String detail) {
            this.code = code;
            this.detail = detail;
        }

        public String code() {
            return code;
        }

        public String detail() {
            return detail;
        }
    }
}
