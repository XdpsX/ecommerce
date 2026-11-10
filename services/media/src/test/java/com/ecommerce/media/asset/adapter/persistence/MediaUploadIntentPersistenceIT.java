package com.ecommerce.media.asset.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.media.asset.application.exception.DirectUploadException;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository.DirectTarget;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository.QuotaLimits;
import com.ecommerce.media.asset.domain.AssetStatus;
import com.ecommerce.media.asset.domain.MediaAsset;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;

@Testcontainers
@SpringBootTest(
        properties = {
            "media.cleanup.enabled=false",
            "media.direct-upload.enabled=false",
            "CLOUDINARY_CLOUD_NAME=test-cloud",
            "CLOUDINARY_API_KEY=test-key",
            "CLOUDINARY_API_SECRET=test-secret"
        })
class MediaUploadIntentPersistenceIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JdbcMediaUploadIntentRepository repository;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM media_upload_discovered_objects");
        jdbc.update("DELETE FROM media_upload_intents");
        jdbc.update("DELETE FROM media_assets");
        jdbc.update("DELETE FROM media_upload_quota_counters");
        jdbc.update("DELETE FROM media_upload_provider_hourly_budget");
        jdbc.update("UPDATE media_upload_controls SET control_value = 'true' WHERE control_key = 'issuance_enabled'");
    }

    @Test
    void createsAtomicBatchAndMapsTheRecoveryNamespace() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        UUID owner = UUID.randomUUID();
        MediaUploadIntent first = intent(owner, "file-a", now);
        MediaUploadIntent second = intent(owner, "file-b", now);

        repository.createBatch(List.of(first, second), limits(10), now);

        assertThat(repository.find(first.id())).get().satisfies(saved -> {
            assertThat(saved.ownerId()).isEqualTo(owner);
            assertThat(saved.discoveryResourceType()).isEqualTo("image");
            assertThat(saved.providerPublicId()).isEqualTo(first.providerPublicId());
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_upload_intents", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT reserved_count FROM media_upload_quota_counters WHERE scope_key = 'global'",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void quotaFailureRollsBackAllCountersAndIntents() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        UUID owner = UUID.randomUUID();

        assertThatThrownBy(() -> repository.createBatch(
                        List.of(intent(owner, "file-a", now), intent(owner, "file-b", now)), limits(1), now))
                .isInstanceOf(DirectUploadException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_upload_intents", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_upload_quota_counters", Integer.class))
                .isZero();
    }

    @Test
    void reservesProviderBudgetAcrossOrdinaryAndRecoveryCalls() {
        Instant hour = Instant.parse("2026-11-06T00:00:00Z");

        assertThat(repository.reserveProviderCall(hour, false, 2, 1)).isTrue();
        assertThat(repository.reserveProviderCall(hour, false, 2, 1)).isFalse();
        assertThat(repository.reserveProviderCall(hour, true, 2, 1)).isTrue();
        assertThat(repository.reserveProviderCall(hour, true, 2, 1)).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT call_count FROM media_upload_provider_hourly_budget WHERE hour_start = ?",
                        Integer.class,
                        java.sql.Timestamp.from(hour)))
                .isEqualTo(2);
    }

    @Test
    void persistsIssuanceStopWhenRecoveryIsMoreThanOneDayOverdue() {
        Instant now = Instant.now();
        UUID owner = UUID.randomUUID();
        MediaUploadIntent expired = intent(owner, "expired", now);
        repository.createBatch(List.of(expired), limits(10), now);
        jdbc.update(
                "UPDATE media_upload_intents SET expires_at = ?, next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(now.minusSeconds(1)),
                java.sql.Timestamp.from(now.minusSeconds(90_000)),
                expired.id());
        QuotaLimits immediateStop = new QuotaLimits(10, 10, 20, 200, 200, java.time.Duration.ofHours(24));

        assertThatThrownBy(() ->
                        repository.createBatch(List.of(intent(UUID.randomUUID(), "new-file", now)), immediateStop, now))
                .isInstanceOf(DirectUploadException.class);

        assertThat(repository.issuanceEnabled()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_upload_intents", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void persistsDeliveryAndIdentityEvidenceForExactDiscoveredObjects() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        UUID owner = UUID.randomUUID();
        MediaUploadIntent intent = intent(owner, "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        String alternateId = intent.providerPublicId() + ".png";
        repository.saveDiscoveredTargets(
                intent.id(), List.of(new DirectTarget("raw", "authenticated", alternateId, "raw-asset-1")), now);

        UUID firstLease = UUID.randomUUID();
        UUID nextLease = UUID.randomUUID();
        var targets = repository.claimDiscoveredTargets(intent.id(), firstLease, now, now.plusSeconds(120), 10);

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.resourceType()).isEqualTo("raw");
            assertThat(target.deliveryType()).isEqualTo("authenticated");
            assertThat(target.publicId()).isEqualTo(alternateId);
            assertThat(target.assetId()).isEqualTo("raw-asset-1");
            assertThat(repository.claimDiscoveredTargets(
                            intent.id(), nextLease, now.plusSeconds(60), now.plusSeconds(180), 10))
                    .isEmpty();
            var reclaimed = repository.claimDiscoveredTargets(
                    intent.id(), nextLease, now.plusSeconds(120), now.plusSeconds(240), 10);
            assertThat(reclaimed).singleElement().satisfies(reclaimedTarget -> {
                assertThat(repository.markTargetDeleted(reclaimedTarget.id(), firstLease, now.plusSeconds(120)))
                        .isFalse();
                assertThat(repository.markTargetDeleted(reclaimedTarget.id(), nextLease, now.plusSeconds(120)))
                        .isTrue();
            });
        });
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM media_upload_discovered_objects WHERE state <> 'DELETED'", Integer.class))
                .isZero();
    }

    @Test
    void rediscoveredReplacementIdentityBecomesCleanupWorkAndFencesOldAcknowledgement() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        MediaUploadIntent intent = intent(UUID.randomUUID(), "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        String publicId = intent.providerPublicId() + ".png";
        repository.saveDiscoveredTargets(
                intent.id(), List.of(new DirectTarget("raw", "authenticated", publicId, "asset-old")), now);

        UUID deletingLease = UUID.randomUUID();
        var deleting = repository
                .claimDiscoveredTargets(intent.id(), deletingLease, now, now.plusSeconds(120), 10)
                .getFirst();
        repository.markTargetDeleted(deleting.id(), deletingLease, now.plusSeconds(1));
        repository.saveDiscoveredTargets(
                intent.id(),
                List.of(new DirectTarget("raw", "authenticated", publicId, "asset-new")),
                now.plusSeconds(2));

        UUID replacementLease = UUID.randomUUID();
        var replacement = repository.claimDiscoveredTargets(
                intent.id(), replacementLease, now.plusSeconds(2), now.plusSeconds(122), 10);
        assertThat(replacement).singleElement().satisfies(target -> {
            assertThat(target.assetId()).isEqualTo("asset-new");
            assertThat(repository.markTargetDeleted(target.id(), deletingLease, now.plusSeconds(3)))
                    .isFalse();
            assertThat(repository.markTargetDeleted(target.id(), replacementLease, now.plusSeconds(3)))
                    .isTrue();
        });
    }

    @Test
    void rediscoveryWithReplacementIdentityFencesAnOutstandingDeleteLease() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        MediaUploadIntent intent = intent(UUID.randomUUID(), "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        String publicId = intent.providerPublicId() + ".png";
        repository.saveDiscoveredTargets(
                intent.id(), List.of(new DirectTarget("raw", "authenticated", publicId, "asset-old")), now);

        UUID staleLease = UUID.randomUUID();
        var oldTarget = repository
                .claimDiscoveredTargets(intent.id(), staleLease, now, now.plusSeconds(120), 10)
                .getFirst();
        repository.saveDiscoveredTargets(
                intent.id(),
                List.of(new DirectTarget("raw", "authenticated", publicId, "asset-new")),
                now.plusSeconds(1));

        UUID currentLease = UUID.randomUUID();
        assertThat(repository.claimDiscoveredTargets(
                        intent.id(), currentLease, now.plusSeconds(60), now.plusSeconds(180), 10))
                .isEmpty();
        assertThat(repository.markTargetDeleted(oldTarget.id(), staleLease, now.plusSeconds(60)))
                .isFalse();
        var replacement = repository.claimDiscoveredTargets(
                intent.id(), currentLease, now.plusSeconds(120), now.plusSeconds(240), 10);
        assertThat(replacement).singleElement().satisfies(target -> {
            assertThat(target.assetId()).isEqualTo("asset-new");
            assertThat(repository.markTargetDeleted(oldTarget.id(), staleLease, now.plusSeconds(120)))
                    .isFalse();
            assertThat(repository.markTargetDeleted(target.id(), currentLease, now.plusSeconds(120)))
                    .isTrue();
        });
    }

    @Test
    void discoveryProgressUsesPostgresTypedTimestampsAndKeepsDiscoveredDeletesDue() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        MediaUploadIntent intent = intent(UUID.randomUUID(), "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        UUID lease = UUID.randomUUID();
        assertThat(repository.claimForCompletion(
                        intent.id(),
                        intent.ownerId(),
                        lease,
                        now,
                        now.plusSeconds(120),
                        java.time.Duration.ofSeconds(5)))
                .isTrue();
        repository.saveDiscoveredTargets(
                intent.id(),
                List.of(new DirectTarget("raw", "authenticated", intent.providerPublicId() + ".png", "raw-asset")),
                now);

        assertThat(repository.saveDiscoveryProgress(intent.id(), lease, "raw", "cursor-1", now.plusSeconds(3_600), now))
                .isTrue();
        assertThat(repository.find(intent.id())).get().satisfies(saved -> {
            assertThat(saved.discoveryResourceType()).isEqualTo("raw");
            assertThat(saved.discoveryCursor()).isEqualTo("cursor-1");
            assertThat(saved.nextAttemptAt()).isEqualTo(now);
        });
    }

    @Test
    void reclaimsAnExpiredLeaseButRejectsStaleAndExpiredFinalization() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        UUID owner = UUID.randomUUID();
        MediaUploadIntent intent = intent(owner, "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        UUID oldLease = UUID.randomUUID();
        UUID currentLease = UUID.randomUUID();
        assertThat(repository.claimForCompletion(
                        intent.id(), owner, oldLease, now, now.plusSeconds(120), java.time.Duration.ofSeconds(5)))
                .isTrue();
        assertThat(repository.claimForCompletion(
                        intent.id(),
                        owner,
                        currentLease,
                        now.plusSeconds(60),
                        now.plusSeconds(180),
                        java.time.Duration.ofSeconds(5)))
                .isFalse();
        assertThat(repository.claimForCompletion(
                        intent.id(),
                        owner,
                        currentLease,
                        now.plusSeconds(120),
                        now.plusSeconds(240),
                        java.time.Duration.ofSeconds(5)))
                .isTrue();
        assertThat(repository.findOwned(UUID.randomUUID(), intent.id())).isEmpty();

        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(
                mediaId,
                owner,
                AssetStatus.READY,
                "image/png",
                200_000,
                100,
                100,
                intent.providerPublicId(),
                "provider-asset-1",
                now,
                now,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                1);
        TrustedProviderInspection inspection = new TrustedProviderInspection(
                "provider-asset-1",
                intent.providerPublicId(),
                "image",
                "authenticated",
                "png",
                200_000L,
                100,
                100,
                "direct-a1-v1",
                intent.id(),
                300_000L,
                200,
                150,
                "png",
                "direct_intent=" + intent.id());

        assertThat(repository.completeAndCreateAsset(
                        intent.id(),
                        oldLease,
                        asset,
                        inspection,
                        now.plusSeconds(121),
                        java.time.Duration.ofSeconds(5)))
                .isFalse();
        assertThat(repository.completeAndCreateAsset(
                        intent.id(),
                        currentLease,
                        asset,
                        inspection,
                        now.plusSeconds(901),
                        java.time.Duration.ofSeconds(5)))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets", Integer.class))
                .isZero();
    }

    @Test
    void finalizationCreatesReadyAssetAndLinksIntentInOneTransaction() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        UUID owner = UUID.randomUUID();
        MediaUploadIntent intent = intent(owner, "file-a", now);
        repository.createBatch(List.of(intent), limits(10), now);
        UUID lease = UUID.randomUUID();
        assertThat(repository.claimForCompletion(
                        intent.id(), owner, lease, now, now.plusSeconds(120), java.time.Duration.ofSeconds(5)))
                .isTrue();

        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(
                mediaId,
                owner,
                AssetStatus.READY,
                "image/png",
                200_000,
                100,
                100,
                intent.providerPublicId(),
                "provider-asset-1",
                now,
                now,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                1);
        TrustedProviderInspection inspection = new TrustedProviderInspection(
                "provider-asset-1",
                intent.providerPublicId(),
                "image",
                "authenticated",
                "png",
                200_000L,
                100,
                100,
                "direct-a1-v1",
                intent.id(),
                300_000L,
                200,
                150,
                "png",
                "direct_intent=" + intent.id());

        assertThat(repository.completeAndCreateAsset(
                        intent.id(), lease, asset, inspection, now, java.time.Duration.ofSeconds(5)))
                .isTrue();
        assertThat(repository.completeAndCreateAsset(
                        intent.id(), lease, asset, inspection, now, java.time.Duration.ofSeconds(5)))
                .isFalse();

        assertThat(repository.find(intent.id())).get().satisfies(saved -> {
            assertThat(saved.state()).isEqualTo(UploadIntentState.COMPLETED);
            assertThat(saved.mediaId()).isEqualTo(mediaId);
            assertThat(saved.providerAssetId()).isEqualTo("provider-asset-1");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM media_assets WHERE id = ?", String.class, mediaId))
                .isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets", Integer.class))
                .isEqualTo(1);
        assertThat(repository.scheduleInspection(intent.providerPublicId(), now.plusSeconds(1)))
                .isFalse();
    }

    private static QuotaLimits limits(int dailyLimit) {
        return new QuotaLimits(dailyLimit, dailyLimit, 20, 200, 200, java.time.Duration.ofHours(24));
    }

    private static MediaUploadIntent intent(UUID owner, String clientFileId, Instant now) {
        UUID id = UUID.randomUUID();
        return new MediaUploadIntent(
                id,
                owner,
                clientFileId,
                "image.png",
                "image/png",
                500_000,
                "direct-a1-v1",
                "ecommerce-media/direct/" + id,
                UploadIntentState.PENDING,
                now,
                now,
                now.plusSeconds(900),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                now.plusSeconds(900),
                now.plusSeconds(3600),
                null,
                "image",
                null,
                null,
                0);
    }
}
