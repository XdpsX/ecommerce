package com.ecommerce.media.configuration;

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
