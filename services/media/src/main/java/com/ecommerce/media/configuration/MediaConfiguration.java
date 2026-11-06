package com.ecommerce.media.configuration;

import java.time.Clock;
import java.util.concurrent.Semaphore;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
@EnableScheduling
public class MediaConfiguration {
    @Bean
    Clock mediaClock() {
        return Clock.systemUTC();
    }

    @Bean
    Semaphore imageDecodePermits(MediaProperties properties) {
        if (properties.getMaxConcurrentDecodes() < 1) {
            throw new IllegalStateException("media.max-concurrent-decodes must be at least 1.");
        }
        return new Semaphore(properties.getMaxConcurrentDecodes());
    }

    @Bean
    org.springframework.boot.ApplicationRunner validateMediaOperationalSettings(MediaProperties properties) {
        return args -> {
            MediaProperties.DirectUpload direct = properties.getDirectUpload();
            if (properties.getDownloadLinkTtl().isZero()
                    || properties.getDownloadLinkTtl().isNegative()
                    || properties.getDownloadLinkTtl().compareTo(java.time.Duration.ofHours(1)) > 0
                    || properties.getProviderTimeout().isZero()
                    || properties.getProviderTimeout().isNegative()
                    || properties.getMaxInputBytes() < 1
                    || properties.getMaxOutputBytes() < 1
                    || properties.getMaxPixels() < 1
                    || properties.getMaxSide() < 1
                    || properties.getCleanup().getBatchSize() < 1
                    || properties.getCleanup().getLeaseDuration().isZero()
                    || properties.getCleanup().getLeaseDuration().compareTo(properties.getProviderTimeout()) <= 0
                    || properties.getCleanup().getAbandonAfter().compareTo(properties.getProviderTimeout()) <= 0
                    || direct.getBatchSize() < 1
                    || direct.getBatchSize() > 10
                    || direct.getMaxFileBytes() < 1
                    || direct.getMaxFileBytes() > 5_242_880
                    || direct.getMaxFileBytes() > properties.getMaxInputBytes()
                    || direct.getMaxBatchBytes() < direct.getMaxFileBytes()
                    || direct.getMaxBatchBytes() > 52_428_800
                    || direct.getPerPrincipalDailyIntents() < 1
                    || direct.getPerPrincipalDailyIntents() > 100
                    || direct.getGlobalDailyIntents() < direct.getPerPrincipalDailyIntents()
                    || direct.getGlobalDailyIntents() > 1_000
                    || direct.getPerPrincipalActiveIntents() < 1
                    || direct.getPerPrincipalActiveIntents() > 20
                    || direct.getGlobalActiveIntents() < direct.getPerPrincipalActiveIntents()
                    || direct.getGlobalActiveIntents() > 200
                    || direct.getProcessConcurrency() < 1
                    || direct.getProcessConcurrency() > 3
                    || positive(direct.getInspectionTimeout()) == false
                    || direct.getInspectionTimeout().compareTo(java.time.Duration.ofSeconds(3)) > 0
                    || positive(direct.getBatchDeadline()) == false
                    || direct.getBatchDeadline().compareTo(java.time.Duration.ofSeconds(10)) > 0
                    || direct.getBatchDeadline().compareTo(direct.getInspectionTimeout()) <= 0
                    || positive(direct.getIntentTtl()) == false
                    || direct.getIntentTtl().compareTo(java.time.Duration.ofMinutes(15)) > 0
                    || direct.getProviderCallsPerHour() < 1
                    || direct.getProviderCallsPerHour() > 120
                    || direct.getReservedRecoveryCallsPerHour() < 1
                    || direct.getReservedRecoveryCallsPerHour() > 30
                    || direct.getReservedRecoveryCallsPerHour() >= direct.getProviderCallsPerHour()
                    || direct.getDiscoveryPagesPerClaim() < 1
                    || direct.getDiscoveryPagesPerClaim() > 3
                    || direct.getDiscoveryPageSize() < 1
                    || direct.getDiscoveryPageSize() > 100
                    || direct.getDeletesPerClaim() < 1
                    || direct.getDeletesPerClaim() > 10
                    || direct.getReconcileBatchSize() < 1
                    || direct.getReconcileBatchSize() > 10
                    || positive(direct.getReconcileInterval()) == false
                    || direct.getReconcileInterval().compareTo(java.time.Duration.ofMinutes(1)) < 0
                    || direct.getReconcileInterval().compareTo(java.time.Duration.ofHours(1)) > 0
                    || positive(direct.getLeaseDuration()) == false
                    || direct.getLeaseDuration().compareTo(direct.getInspectionTimeout()) <= 0
                    || positive(direct.getRetryCap()) == false
                    || direct.getRetryCap().compareTo(java.time.Duration.ofSeconds(60)) < 0
                    || direct.getRetryCap().compareTo(java.time.Duration.ofHours(1)) > 0
                    || direct.getIssuanceStopBacklog() < 1
                    || direct.getIssuanceStopBacklog() > 200
                    || positive(direct.getIssuanceStopOldestRecovery()) == false
                    || direct.getIssuanceStopOldestRecovery().compareTo(java.time.Duration.ofHours(24)) > 0
                    || direct.getMaxNotificationBytes() < 1
                    || direct.getMaxNotificationBytes() > 65_536
                    || positive(direct.getNotificationMaxAge()) == false
                    || direct.getNotificationMaxAge().compareTo(java.time.Duration.ofMinutes(5)) > 0
                    || direct.getNotificationFutureSkew().isNegative()
                    || direct.getNotificationFutureSkew().compareTo(java.time.Duration.ofSeconds(30)) > 0) {
                throw new IllegalStateException(
                        "Media size, TTL and cleanup settings must be positive and within safe bounds.");
            }
            if (direct.isWebhookEnabled()
                    && (!direct.isEnabled()
                            || !validHttpsUrl(direct.getNotificationUrl())
                            || blank(direct.getNotificationApiKey()))) {
                throw new IllegalStateException(
                        "Direct upload and a server-owned HTTPS notification URL/key are required when webhooks are enabled.");
            }
            MediaProperties.Cloudinary cloudinary = properties.getStorage().getCloudinary();
            if (direct.isEnabled()
                    && (blank(cloudinary.getCloudName())
                            || blank(cloudinary.getApiKey())
                            || blank(cloudinary.getApiSecret()))) {
                throw new IllegalStateException("Cloudinary credentials are required when direct upload is enabled.");
            }
        };
    }

    private static boolean positive(java.time.Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private static boolean validHttpsUrl(String value) {
        if (blank(value)) return false;
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
