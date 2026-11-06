package com.ecommerce.media.asset.application.exception;

public final class MediaPersistenceLockTimeoutException extends RuntimeException {
    public MediaPersistenceLockTimeoutException(Throwable cause) {
        super("A media persistence lock could not be acquired within the allowed time.", cause);
    }
}
