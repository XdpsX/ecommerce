package com.ecommerce.media.asset.application.model;

public record CanonicalImage(byte[] bytes, String mediaType, String format, int width, int height) {}
