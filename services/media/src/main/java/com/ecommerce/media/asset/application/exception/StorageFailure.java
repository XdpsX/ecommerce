package com.ecommerce.media.asset.application.exception;

public final class StorageFailure extends RuntimeException {
    private final Category category;

    public StorageFailure(Category category) {
        super(category.name());
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public enum Category {
        UPLOAD_OUTCOME_UNKNOWN,
        PROVIDER_INSPECTION_FAILED,
        DOWNLOAD_LINK_GENERATION_FAILED,
        DELETE_OUTCOME_UNKNOWN,
        PROVIDER_IDENTITY_MISMATCH,
        OPERATION_FAILED
    }
}
