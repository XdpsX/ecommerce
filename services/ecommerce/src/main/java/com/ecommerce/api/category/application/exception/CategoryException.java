package com.ecommerce.api.category.application.exception;

public class CategoryException extends RuntimeException {
    private final Kind kind;

    public CategoryException(Kind kind, String detail) {
        super(detail);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return kind.name();
    }

    public enum Kind {
        BAD_REQUEST,
        CATEGORY_NOT_FOUND,
        CATEGORY_VERSION_CONFLICT,
        CATEGORY_STRUCTURE_CONFLICT,
        CATEGORY_SLUG_CONFLICT,
        CATEGORY_IN_USE,
        INVALID_CATEGORY_HIERARCHY,
        CATEGORY_DEPTH_EXCEEDED
    }
}
