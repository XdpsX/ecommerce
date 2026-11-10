package com.ecommerce.media.asset.application.model;

import java.time.Duration;

/** Immutable lifecycle values consumed by the media asset use case. */
public record MediaLifecycleSettings(
        Duration downloadLinkTtl,
        Duration abandonAfter,
        int cleanupBatchSize,
        Duration cleanupLeaseDuration,
        Duration retryCap) {}
