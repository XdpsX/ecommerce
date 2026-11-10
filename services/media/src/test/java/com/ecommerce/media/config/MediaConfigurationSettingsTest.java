package com.ecommerce.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MediaConfigurationSettingsTest {
    @Test
    void mapsDistinctBoundValuesIntoImmutableApplicationSettings() {
        MediaProperties properties = new MediaProperties();
        properties.setMaxInputBytes(1_234_567);
        properties.setMaxOutputBytes(2_345_678);
        properties.setMaxPixels(7_654_321);
        properties.setMaxSide(4_321);
        properties.setDownloadLinkTtl(Duration.ofSeconds(73));
        properties.getCleanup().setAbandonAfter(Duration.ofMinutes(17));
        properties.getCleanup().setBatchSize(6);
        properties.getCleanup().setLeaseDuration(Duration.ofMinutes(4));
        properties.getCleanup().setRetryCap(Duration.ofMinutes(19));
        properties.getStorage().getCloudinary().setFolder("assets/catalog");
        MediaProperties.DirectUpload direct = properties.getDirectUpload();
        direct.setEnabled(true);
        direct.setIssuanceEnabled(false);
        direct.setWebhookEnabled(true);
        direct.setNotificationUrl("https://media.example.test/notify");
        direct.setNotificationApiKey("server-secret");
        direct.setBatchSize(7);
        direct.setMaxFileBytes(1_111_111);
        direct.setMaxBatchBytes(9_876_543);
        direct.setPerPrincipalDailyIntents(44);
        direct.setGlobalDailyIntents(444);
        direct.setPerPrincipalActiveIntents(8);
        direct.setGlobalActiveIntents(88);
        direct.setInspectionTimeout(Duration.ofSeconds(2));
        direct.setBatchDeadline(Duration.ofSeconds(8));
        direct.setIntentTtl(Duration.ofMinutes(12));
        direct.setProviderCallsPerHour(99);
        direct.setReservedRecoveryCallsPerHour(21);
        direct.setDiscoveryPagesPerClaim(2);
        direct.setDiscoveryPageSize(71);
        direct.setDeletesPerClaim(5);
        direct.setReconcileBatchSize(6);
        direct.setReconcileInterval(Duration.ofMinutes(7));
        direct.setLeaseDuration(Duration.ofMinutes(5));
        direct.setRetryCap(Duration.ofMinutes(23));
        direct.setIssuanceStopBacklog(77);
        direct.setIssuanceStopOldestRecovery(Duration.ofHours(9));

        MediaConfiguration configuration = new MediaConfiguration();

        var image = configuration.imagePolicy(properties);
        var lifecycle = configuration.mediaLifecycleSettings(properties);
        var upload = configuration.directUploadSettings(properties);

        assertThat(image.maxInputBytes()).isEqualTo(1_234_567);
        assertThat(image.maxOutputBytes()).isEqualTo(2_345_678);
        assertThat(image.maxPixels()).isEqualTo(7_654_321);
        assertThat(image.maxSide()).isEqualTo(4_321);
        assertThat(lifecycle.downloadLinkTtl()).isEqualTo(Duration.ofSeconds(73));
        assertThat(lifecycle.abandonAfter()).isEqualTo(Duration.ofMinutes(17));
        assertThat(lifecycle.cleanupBatchSize()).isEqualTo(6);
        assertThat(lifecycle.cleanupLeaseDuration()).isEqualTo(Duration.ofMinutes(4));
        assertThat(lifecycle.retryCap()).isEqualTo(Duration.ofMinutes(19));
        assertThat(upload.enabled()).isTrue();
        assertThat(upload.issuanceEnabled()).isFalse();
        assertThat(upload.webhookEnabled()).isTrue();
        assertThat(upload.webhookCredentialConfigured()).isTrue();
        assertThat(upload.notificationUrl()).isEqualTo("https://media.example.test/notify");
        assertThat(upload.assetFolder()).isEqualTo("assets/catalog");
        assertThat(upload.batchSize()).isEqualTo(7);
        assertThat(upload.maxFileBytes()).isEqualTo(1_111_111);
        assertThat(upload.maxBatchBytes()).isEqualTo(9_876_543);
        assertThat(upload.perPrincipalDailyIntents()).isEqualTo(44);
        assertThat(upload.globalDailyIntents()).isEqualTo(444);
        assertThat(upload.perPrincipalActiveIntents()).isEqualTo(8);
        assertThat(upload.globalActiveIntents()).isEqualTo(88);
        assertThat(upload.inspectionTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(upload.batchDeadline()).isEqualTo(Duration.ofSeconds(8));
        assertThat(upload.intentTtl()).isEqualTo(Duration.ofMinutes(12));
        assertThat(upload.providerCallsPerHour()).isEqualTo(99);
        assertThat(upload.reservedRecoveryCallsPerHour()).isEqualTo(21);
        assertThat(upload.discoveryPagesPerClaim()).isEqualTo(2);
        assertThat(upload.discoveryPageSize()).isEqualTo(71);
        assertThat(upload.deletesPerClaim()).isEqualTo(5);
        assertThat(upload.reconcileBatchSize()).isEqualTo(6);
        assertThat(upload.reconcileInterval()).isEqualTo(Duration.ofMinutes(7));
        assertThat(upload.leaseDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(upload.retryCap()).isEqualTo(Duration.ofMinutes(23));
        assertThat(upload.issuanceStopBacklog()).isEqualTo(77);
        assertThat(upload.issuanceStopOldestRecovery()).isEqualTo(Duration.ofHours(9));
    }
}
