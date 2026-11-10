package com.ecommerce.media.asset.application.model;

import java.time.Duration;

/** Immutable application policy copied from validated service configuration at startup. */
public record DirectUploadSettings(
        boolean enabled,
        boolean issuanceEnabled,
        boolean webhookEnabled,
        boolean webhookCredentialConfigured,
        String notificationUrl,
        String assetFolder,
        int batchSize,
        long maxFileBytes,
        long maxBatchBytes,
        int perPrincipalDailyIntents,
        int globalDailyIntents,
        int perPrincipalActiveIntents,
        int globalActiveIntents,
        Duration inspectionTimeout,
        Duration batchDeadline,
        Duration intentTtl,
        int providerCallsPerHour,
        int reservedRecoveryCallsPerHour,
        int discoveryPagesPerClaim,
        int discoveryPageSize,
        int deletesPerClaim,
        int reconcileBatchSize,
        Duration reconcileInterval,
        Duration leaseDuration,
        Duration retryCap,
        int issuanceStopBacklog,
        Duration issuanceStopOldestRecovery) {}
