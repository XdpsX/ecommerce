package com.ecommerce.media.asset.application.model;

/** Immutable image bounds used by server-side canonicalization and verification. */
public record ImagePolicy(int maxInputBytes, int maxOutputBytes, long maxPixels, int maxSide) {}
