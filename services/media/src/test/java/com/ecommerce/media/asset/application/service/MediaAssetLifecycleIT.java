package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ecommerce.media.asset.application.exception.MediaException;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.asset.application.model.MediaAssetView;
import com.ecommerce.media.asset.application.port.MediaAssetRepository;
import com.ecommerce.media.asset.application.port.MediaStorage;
import com.ecommerce.media.asset.domain.AssetStatus;
import com.ecommerce.media.asset.domain.MediaAsset;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "media.cleanup.enabled=false",
            "CLOUDINARY_CLOUD_NAME=test-cloud",
            "CLOUDINARY_API_KEY=test-key",
            "CLOUDINARY_API_SECRET=test-secret"
        })
@Import(MediaAssetLifecycleIT.StorageFixtureConfiguration.class)
class MediaAssetLifecycleIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MediaAssetService service;

    @Autowired
    MediaAssetRepository repository;

    @Autowired
    TestClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageFixture storage;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void clearState() {
        jdbc.update("DELETE FROM media_assets");
        storage.deleteResult.set(MediaStorage.DeleteResult.DELETED);
        storage.failUpload.set(false);
        clock.set(Instant.now());
    }

    @Test
    void persistsReadyMetadataAndReturnsAnExpiringLinkWithoutProviderIdentifiers() throws Exception {
        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));

        assertThat(uploaded.status()).isEqualTo(AssetStatus.READY.name());
        assertThat(uploaded.mediaType()).isEqualTo("image/png");
        assertThat(uploaded.width()).isEqualTo(4);
        assertThat(uploaded.byteSize()).isPositive();
        assertThat(service.downloadLink(uploaded.id()).url()).startsWith("https://fixture.invalid/");
        assertThat(jdbc.queryForObject(
                        "SELECT provider_asset_id FROM media_assets WHERE id = ?", String.class, uploaded.id()))
                .isEqualTo("provider-asset");
    }

    @Test
    void deletionIsDurableIdempotentAndCompletedByTheCleanupWorker() throws Exception {
        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));
        service.requestDelete(uploaded.id());
        service.requestDelete(uploaded.id());
        assertThatThrownBy(() -> service.get(uploaded.id()))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("MEDIA_NOT_FOUND");

        assertThat(service.runCleanupBatch().deleted()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM media_assets WHERE id = ?", String.class, uploaded.id()))
                .isEqualTo(AssetStatus.DELETED.name());
        assertThatThrownBy(() -> service.get(uploaded.id()))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("MEDIA_NOT_FOUND");
    }

    @Test
    void uncertainUploadRemainsARecoverableTombstoneAfterNotFoundAndDeleteResponses() throws Exception {
        storage.failUpload.set(true);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), new ByteArrayInputStream(png())))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("MEDIA_PROVIDER_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM media_assets", String.class))
                .isEqualTo(AssetStatus.AMBIGUOUS_UPLOAD.name());

        jdbc.update(
                "UPDATE media_assets SET next_attempt_at = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
        storage.deleteResult.set(MediaStorage.DeleteResult.NOT_FOUND);
        service.runCleanupBatch();

        assertThat(jdbc.queryForObject("SELECT status FROM media_assets", String.class))
                .isEqualTo(AssetStatus.AMBIGUOUS_UPLOAD.name());
        assertThat(jdbc.queryForObject("SELECT lease_id FROM media_assets", UUID.class))
                .isNull();

        jdbc.update(
                "UPDATE media_assets SET next_attempt_at = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
        storage.deleteResult.set(MediaStorage.DeleteResult.DELETED);
        service.runCleanupBatch();

        assertThat(jdbc.queryForObject("SELECT status FROM media_assets", String.class))
                .isEqualTo(AssetStatus.AMBIGUOUS_UPLOAD.name());
        assertThat(jdbc.queryForObject("SELECT last_error_category FROM media_assets", String.class))
                .isEqualTo("UPLOAD_MAY_STILL_COMPLETE_AFTER_DELETE");
    }

    @Test
    void expiredDeleteLeaseIsReclaimedAndStaleWorkerCannotCompleteIt() throws Exception {
        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));
        service.requestDelete(uploaded.id());
        Instant started = clock.instant();
        MediaAsset first = claim(started, started.minusSeconds(600), started.plusSeconds(120));

        assertThat(repository.claimCleanup(
                        started.plusSeconds(60), started.minusSeconds(540), started.plusSeconds(180), 1))
                .isEmpty();
        MediaAsset second = claim(started.plusSeconds(121), started.minusSeconds(479), started.plusSeconds(241));
        assertThat(second.leaseId()).isNotEqualTo(first.leaseId());

        repository.cleanupDeleted(uploaded.id(), first.leaseId(), started.plusSeconds(122));
        MediaAsset stillOwnedBySecondWorker = repository.find(uploaded.id()).orElseThrow();
        assertThat(stillOwnedBySecondWorker.status()).isEqualTo(AssetStatus.DELETE_PENDING);
        assertThat(stillOwnedBySecondWorker.leaseId()).isEqualTo(second.leaseId());

        repository.cleanupDeleted(uploaded.id(), second.leaseId(), started.plusSeconds(123));
        assertThat(repository.find(uploaded.id()).orElseThrow().status()).isEqualTo(AssetStatus.DELETED);
    }

    @Test
    void cleanupClaimCanBeTakenByOnlyOneConcurrentWorker() throws Exception {
        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));
        service.requestDelete(uploaded.id());
        Instant now = clock.instant();

        var first = CompletableFuture.supplyAsync(
                () -> repository.claimCleanup(now, now.minusSeconds(600), now.plusSeconds(120), 1));
        var second = CompletableFuture.supplyAsync(
                () -> repository.claimCleanup(now, now.minusSeconds(600), now.plusSeconds(120), 1));

        assertThat(first.get().size() + second.get().size()).isEqualTo(1);
    }

    @Test
    void abandonedUploadLeaseCanBeReclaimedWithoutClearingTheNewLease() {
        Instant created = clock.instant();
        UUID id = UUID.randomUUID();
        repository.createUploading(new MediaAsset(
                id,
                UUID.randomUUID(),
                AssetStatus.UPLOADING,
                "image/png",
                16,
                4,
                4,
                storage.allocateObjectKey(id),
                null,
                created,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                0));

        MediaAsset first = claim(created.plusSeconds(660), created.plusSeconds(60), created.plusSeconds(780));
        assertThat(first.status()).isEqualTo(AssetStatus.AMBIGUOUS_UPLOAD);
        MediaAsset second = claim(created.plusSeconds(781), created.plusSeconds(181), created.plusSeconds(901));
        assertThat(second.leaseId()).isNotEqualTo(first.leaseId());

        repository.markAmbiguousUpload(id, "STALE_UPLOAD_RESULT", created.plusSeconds(900), created.plusSeconds(800));
        repository.cleanupAmbiguousPending(
                id, first.leaseId(), "STALE_WORKER_RESULT", created.plusSeconds(900), created.plusSeconds(800));
        assertThat(repository.find(id).orElseThrow().leaseId()).isEqualTo(second.leaseId());

        repository.cleanupAmbiguousPending(
                id,
                second.leaseId(),
                "PROVIDER_NOT_FOUND_PENDING_RECONCILIATION",
                created.plusSeconds(1_000),
                created.plusSeconds(800));
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo(AssetStatus.AMBIGUOUS_UPLOAD);
    }

    @Test
    void retriesDeleteAfterProviderSuccessAndLostDatabaseCompletion() throws Exception {
        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));
        service.requestDelete(uploaded.id());
        Instant now = clock.instant();
        MediaAsset lostWorker = claim(now, now.minusSeconds(600), now.plusSeconds(120));
        assertThat(lostWorker.status()).isEqualTo(AssetStatus.DELETE_PENDING);

        clock.set(now.plusSeconds(121));
        storage.deleteResult.set(MediaStorage.DeleteResult.NOT_FOUND);
        assertThat(service.runCleanupBatch().deleted()).isEqualTo(1);
        assertThat(repository.find(uploaded.id()).orElseThrow().status()).isEqualTo(AssetStatus.DELETED);
    }

    private MediaAsset claim(Instant now, Instant abandonedBefore, Instant leaseUntil) {
        return repository.claimCleanup(now, abandonedBefore, leaseUntil, 1).getFirst();
    }

    @Test
    void resourceServerRequiresMediaScopeAndOperationPermissionRatherThanRolesAlone() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/admin/media/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/media/{id}", id).with(token("", java.util.List.of("media:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/media/{id}", id).with(token("media.api", java.util.List.of("media:upload"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/media/{id}", id).with(token("media.api", java.util.List.of("media:read"))))
                .andExpect(status().isNotFound());

        MediaAssetView uploaded = service.upload(UUID.randomUUID(), new ByteArrayInputStream(png()));
        mvc.perform(post("/api/admin/media/{id}/download-link", uploaded.id())
                        .with(token("media.api", java.util.List.of("media:read"))))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor token(String scope, java.util.List<String> permissions) {
        return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString())
                .claim("scope", scope)
                .claim("permissions", permissions)
                .claim("roles", java.util.List.of("SUPER_ADMIN")));
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @TestConfiguration
    static class StorageFixtureConfiguration {
        @Bean
        StorageFixture storageFixture() {
            return new StorageFixture();
        }

        @Bean
        @Primary
        MediaStorage mediaStorage(StorageFixture fixture) {
            return fixture;
        }

        @Bean
        @Primary
        TestClock mediaTestClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    static class StorageFixture implements MediaStorage {
        final AtomicReference<DeleteResult> deleteResult = new AtomicReference<>(DeleteResult.DELETED);
        final java.util.concurrent.atomic.AtomicBoolean failUpload = new java.util.concurrent.atomic.AtomicBoolean();

        @Override
        public String allocateObjectKey(UUID mediaId) {
            return mediaId.toString();
        }

        @Override
        public StoredAsset upload(String publicId, CanonicalImage image) {
            if (failUpload.get()) throw new IllegalStateException("simulated timeout");
            return new StoredAsset("provider-asset", publicId, "image", "authenticated", image.format());
        }

        @Override
        public StoredAsset inspect(String publicId) {
            return new StoredAsset("provider-asset", publicId, "image", "authenticated", "png");
        }

        @Override
        public String downloadLink(String publicId, String format, Instant expiresAt) {
            return "https://fixture.invalid/" + UUID.randomUUID();
        }

        @Override
        public DeleteResult delete(String publicId) {
            return deleteResult.get();
        }
    }
}
