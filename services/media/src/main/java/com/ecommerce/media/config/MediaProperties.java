package com.ecommerce.media.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public class MediaProperties {
    private String audience = "media-api";
    private Duration downloadLinkTtl = Duration.ofSeconds(60);
    private Duration providerTimeout = Duration.ofSeconds(30);
    private int maxInputBytes = 5 * 1024 * 1024;
    private int maxOutputBytes = 4 * 1024 * 1024;
    private long maxPixels = 20_000_000;
    private int maxSide = 10_000;
    private int maxConcurrentDecodes = 1;
    private final Storage storage = new Storage();
    private final Cleanup cleanup = new Cleanup();
    private final DirectUpload directUpload = new DirectUpload();

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getDownloadLinkTtl() {
        return downloadLinkTtl;
    }

    public void setDownloadLinkTtl(Duration value) {
        this.downloadLinkTtl = value;
    }

    public Duration getProviderTimeout() {
        return providerTimeout;
    }

    public void setProviderTimeout(Duration value) {
        this.providerTimeout = value;
    }

    public int getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(int value) {
        this.maxInputBytes = value;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int value) {
        this.maxOutputBytes = value;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(long value) {
        this.maxPixels = value;
    }

    public int getMaxSide() {
        return maxSide;
    }

    public void setMaxSide(int value) {
        this.maxSide = value;
    }

    public int getMaxConcurrentDecodes() {
        return maxConcurrentDecodes;
    }

    public void setMaxConcurrentDecodes(int value) {
        this.maxConcurrentDecodes = value;
    }

    public Storage getStorage() {
        return storage;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public DirectUpload getDirectUpload() {
        return directUpload;
    }

    public static class DirectUpload {
        private boolean enabled;
        private boolean issuanceEnabled = true;
        private boolean webhookEnabled;
        private String notificationUrl = "";
        private String notificationApiKey = "";
        private int batchSize = 10;
        private long maxFileBytes = 5_242_880;
        private long maxBatchBytes = 52_428_800;
        private int perPrincipalDailyIntents = 100;
        private int globalDailyIntents = 1_000;
        private int perPrincipalActiveIntents = 20;
        private int globalActiveIntents = 200;
        private int processConcurrency = 3;
        private Duration inspectionTimeout = Duration.ofSeconds(3);
        private Duration batchDeadline = Duration.ofSeconds(10);
        private Duration intentTtl = Duration.ofMinutes(15);
        private int providerCallsPerHour = 120;
        private int reservedRecoveryCallsPerHour = 30;
        private int discoveryPagesPerClaim = 3;
        private int discoveryPageSize = 100;
        private int deletesPerClaim = 10;
        private int reconcileBatchSize = 10;
        private Duration reconcileInterval = Duration.ofMinutes(1);
        private Duration leaseDuration = Duration.ofMinutes(2);
        private Duration retryCap = Duration.ofHours(1);
        private int issuanceStopBacklog = 200;
        private Duration issuanceStopOldestRecovery = Duration.ofHours(24);
        private int maxNotificationBytes = 65_536;
        private Duration notificationMaxAge = Duration.ofMinutes(5);
        private Duration notificationFutureSkew = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public boolean isIssuanceEnabled() {
            return issuanceEnabled;
        }

        public void setIssuanceEnabled(boolean value) {
            issuanceEnabled = value;
        }

        public boolean isWebhookEnabled() {
            return webhookEnabled;
        }

        public void setWebhookEnabled(boolean value) {
            webhookEnabled = value;
        }

        public String getNotificationUrl() {
            return notificationUrl;
        }

        public void setNotificationUrl(String value) {
            notificationUrl = value;
        }

        public String getNotificationApiKey() {
            return notificationApiKey;
        }

        public void setNotificationApiKey(String value) {
            notificationApiKey = value;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int value) {
            batchSize = value;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long value) {
            maxFileBytes = value;
        }

        public long getMaxBatchBytes() {
            return maxBatchBytes;
        }

        public void setMaxBatchBytes(long value) {
            maxBatchBytes = value;
        }

        public int getPerPrincipalDailyIntents() {
            return perPrincipalDailyIntents;
        }

        public void setPerPrincipalDailyIntents(int value) {
            perPrincipalDailyIntents = value;
        }

        public int getGlobalDailyIntents() {
            return globalDailyIntents;
        }

        public void setGlobalDailyIntents(int value) {
            globalDailyIntents = value;
        }

        public int getPerPrincipalActiveIntents() {
            return perPrincipalActiveIntents;
        }

        public void setPerPrincipalActiveIntents(int value) {
            perPrincipalActiveIntents = value;
        }

        public int getGlobalActiveIntents() {
            return globalActiveIntents;
        }

        public void setGlobalActiveIntents(int value) {
            globalActiveIntents = value;
        }

        public int getProcessConcurrency() {
            return processConcurrency;
        }

        public void setProcessConcurrency(int value) {
            processConcurrency = value;
        }

        public Duration getInspectionTimeout() {
            return inspectionTimeout;
        }

        public void setInspectionTimeout(Duration value) {
            inspectionTimeout = value;
        }

        public Duration getBatchDeadline() {
            return batchDeadline;
        }

        public void setBatchDeadline(Duration value) {
            batchDeadline = value;
        }

        public Duration getIntentTtl() {
            return intentTtl;
        }

        public void setIntentTtl(Duration value) {
            intentTtl = value;
        }

        public int getProviderCallsPerHour() {
            return providerCallsPerHour;
        }

        public void setProviderCallsPerHour(int value) {
            providerCallsPerHour = value;
        }

        public int getReservedRecoveryCallsPerHour() {
            return reservedRecoveryCallsPerHour;
        }

        public void setReservedRecoveryCallsPerHour(int value) {
            reservedRecoveryCallsPerHour = value;
        }

        public int getDiscoveryPagesPerClaim() {
            return discoveryPagesPerClaim;
        }

        public void setDiscoveryPagesPerClaim(int value) {
            discoveryPagesPerClaim = value;
        }

        public int getDiscoveryPageSize() {
            return discoveryPageSize;
        }

        public void setDiscoveryPageSize(int value) {
            discoveryPageSize = value;
        }

        public int getDeletesPerClaim() {
            return deletesPerClaim;
        }

        public void setDeletesPerClaim(int value) {
            deletesPerClaim = value;
        }

        public int getReconcileBatchSize() {
            return reconcileBatchSize;
        }

        public void setReconcileBatchSize(int value) {
            reconcileBatchSize = value;
        }

        public Duration getReconcileInterval() {
            return reconcileInterval;
        }

        public void setReconcileInterval(Duration value) {
            reconcileInterval = value;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration value) {
            leaseDuration = value;
        }

        public Duration getRetryCap() {
            return retryCap;
        }

        public void setRetryCap(Duration value) {
            retryCap = value;
        }

        public int getIssuanceStopBacklog() {
            return issuanceStopBacklog;
        }

        public void setIssuanceStopBacklog(int value) {
            issuanceStopBacklog = value;
        }

        public Duration getIssuanceStopOldestRecovery() {
            return issuanceStopOldestRecovery;
        }

        public void setIssuanceStopOldestRecovery(Duration value) {
            issuanceStopOldestRecovery = value;
        }

        public int getMaxNotificationBytes() {
            return maxNotificationBytes;
        }

        public void setMaxNotificationBytes(int value) {
            maxNotificationBytes = value;
        }

        public Duration getNotificationMaxAge() {
            return notificationMaxAge;
        }

        public void setNotificationMaxAge(Duration value) {
            notificationMaxAge = value;
        }

        public Duration getNotificationFutureSkew() {
            return notificationFutureSkew;
        }

        public void setNotificationFutureSkew(Duration value) {
            notificationFutureSkew = value;
        }
    }

    public static class Storage {
        private final Cloudinary cloudinary = new Cloudinary();

        public Cloudinary getCloudinary() {
            return cloudinary;
        }
    }

    public static class Cloudinary {
        private String cloudName = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String folder = "ecommerce-media";

        public String getCloudName() {
            return cloudName;
        }

        public void setCloudName(String value) {
            cloudName = value;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            apiKey = value;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String value) {
            apiSecret = value;
        }

        public String getFolder() {
            return folder;
        }

        public void setFolder(String value) {
            folder = value;
        }
    }

    public static class Cleanup {
        private boolean enabled = true;
        private Duration interval = Duration.ofMinutes(1);
        private Duration abandonAfter = Duration.ofMinutes(10);
        private int batchSize = 10;
        private Duration leaseDuration = Duration.ofMinutes(2);
        private Duration retryCap = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration value) {
            interval = value;
        }

        public Duration getAbandonAfter() {
            return abandonAfter;
        }

        public void setAbandonAfter(Duration value) {
            abandonAfter = value;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int value) {
            batchSize = value;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration value) {
            leaseDuration = value;
        }

        public Duration getRetryCap() {
            return retryCap;
        }

        public void setRetryCap(Duration value) {
            retryCap = value;
        }
    }
}
