package com.ecommerce.media.asset.application.model;

public record UploadIntentDeclaration(String clientFileId, String filename, String contentType, long sizeBytes) {}
