package com.ecommerce.media.asset.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.DiscoveredObject;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.DiscoveryPage;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.SignedUpload;
import com.ecommerce.media.asset.application.service.MediaUploadRecoveryService;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "media.cleanup.enabled=false",
            "media.direct-upload.enabled=true",
            "media.direct-upload.batch-deadline=PT2S",
            "media.direct-upload.inspection-timeout=PT1S",
            "media.direct-upload.reconcile-interval=PT1H",
            "CLOUDINARY_CLOUD_NAME=test-cloud",
            "CLOUDINARY_API_KEY=test-key",
            "CLOUDINARY_API_SECRET=test-secret"
        })
@Import(MediaUploadIntentApiIT.StorageFixtureConfiguration.class)
class MediaUploadIntentApiIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DirectUploadStorage storage;

    @Autowired
    MediaUploadRecoveryService recovery;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JsonMapper json;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM media_upload_discovered_objects");
        jdbc.update("DELETE FROM media_upload_intents");
        jdbc.update("DELETE FROM media_assets");
        jdbc.update("DELETE FROM media_upload_quota_counters");
        jdbc.update("DELETE FROM media_upload_provider_hourly_budget");
        jdbc.update("UPDATE media_upload_controls SET control_value = 'true' WHERE control_key = 'issuance_enabled'");
        reset(storage);
        when(storage.sign(any()))
                .thenReturn(new SignedUpload(
                        "https://upload.example.test/image/upload",
                        Map.of("api_key", "fixture", "signature", "signed")));
    }

    @Test
    void createsCompletesAndRepeatsOwnedIntentWithoutTrustingClientMetadata() throws Exception {
        UUID owner = UUID.randomUUID();
        String intentId = create(owner);
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> inspection(invocation.getArgument(0)));

        String completeRequest = "{\"intentIds\":[\"" + intentId + "\"]}";
        mvc.perform(post("/api/admin/media/upload-intents/complete")
                        .with(admin(owner))
                        .contentType("application/json")
                        .content(completeRequest))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].status").value("READY"));

        mvc.perform(post("/api/admin/media/upload-intents/complete")
                        .with(admin(owner))
                        .contentType("application/json")
                        .content(completeRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("READY"));

        mvc.perform(post("/api/admin/media/upload-intents/status")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(completeRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("NOT_FOUND"));

        verify(storage, times(1)).inspect(anyString(), any());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject("SELECT COUNT(*) FROM media_assets", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void returnsCurrentUnavailableStateAfterDeletionWithoutResurrection() throws Exception {
        UUID owner = UUID.randomUUID();
        String intentId = create(owner);
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> inspection(invocation.getArgument(0)));
        String completeRequest = "{\"intentIds\":[\"" + intentId + "\"]}";
        MvcResult result = mvc.perform(post("/api/admin/media/upload-intents/complete")
                        .with(admin(owner))
                        .contentType("application/json")
                        .content(completeRequest))
                .andExpect(status().isOk())
                .andReturn();
        String mediaId = JsonPathSupport.readString(result, "$.items[0].mediaId", json);
        jdbc.update(
                "UPDATE media_assets SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                UUID.fromString(mediaId));

        mvc.perform(post("/api/admin/media/upload-intents/complete")
                        .with(admin(owner))
                        .contentType("application/json")
                        .content(completeRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DELETED"));
        verify(storage, times(1)).inspect(anyString(), any());
    }

    @Test
    void recoveryPersistsDiscoveryProgressAndDeletesExactTargetInPostgres() throws Exception {
        UUID owner = UUID.randomUUID();
        String intentId = create(owner);
        String publicId = jdbc.queryForObject(
                "SELECT provider_public_id FROM media_upload_intents WHERE id = ?",
                String.class,
                UUID.fromString(intentId));
        String discoveredId = publicId + ".png";
        jdbc.update(
                "UPDATE media_upload_intents SET state = 'EXPIRED', result_status = 'EXPIRED', "
                        + "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute', "
                        + "next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
                UUID.fromString(intentId));
        when(storage.discover(anyString(), anyString(), any(), anyInt())).thenAnswer(invocation -> {
            String resourceType = invocation.getArgument(1);
            if ("image".equals(resourceType)) {
                return new DiscoveryPage(
                        List.of(new DiscoveredObject("image", "authenticated", discoveredId, "asset-discovered")),
                        null);
            }
            return new DiscoveryPage(List.of(), null);
        });

        recovery.runBatch();

        assertThat(jdbc.queryForObject(
                        "SELECT state FROM media_upload_discovered_objects WHERE intent_id = ? AND public_id = ?",
                        String.class,
                        UUID.fromString(intentId),
                        discoveredId))
                .isEqualTo("DELETED");
        verify(storage).delete(new DiscoveredObject("image", "authenticated", discoveredId, "asset-discovered"));
    }

    @Test
    void pollingFinalizesUploadBeforeExpiryWithoutCompletionOrWebhook() throws Exception {
        UUID owner = UUID.randomUUID();
        String intentId = create(owner);
        InstantPair schedule = jdbc.queryForObject(
                "SELECT next_attempt_at, expires_at FROM media_upload_intents WHERE id = ?",
                (rs, row) -> new InstantPair(
                        rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                UUID.fromString(intentId));
        assertThat(schedule.nextAttemptAt()).isBefore(schedule.expiresAt());
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> inspection(invocation.getArgument(0)));
        jdbc.update(
                "UPDATE media_upload_intents SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
                UUID.fromString(intentId));

        recovery.runBatch();

        assertThat(jdbc.queryForObject(
                        "SELECT state FROM media_upload_intents WHERE id = ?", String.class, UUID.fromString(intentId)))
                .isEqualTo("COMPLETED");
        verify(storage, times(1)).inspect(anyString(), any());
    }

    @Test
    void pollingExpiresUploadWithoutInspectingItAfterExpiry() throws Exception {
        UUID owner = UUID.randomUUID();
        String intentId = create(owner);
        jdbc.update(
                "UPDATE media_upload_intents SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second', "
                        + "next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
                UUID.fromString(intentId));

        recovery.runBatch();

        assertThat(jdbc.queryForObject(
                        "SELECT state FROM media_upload_intents WHERE id = ?", String.class, UUID.fromString(intentId)))
                .isEqualTo("EXPIRED");
        verify(storage, org.mockito.Mockito.never()).inspect(anyString(), any());
    }

    @Test
    void rowLockWaitConsumesBatchDeadlineAndPreventsLateProviderInspection() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intent = UUID.fromString(create(owner));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder =
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.update("UPDATE media_upload_intents SET version = version + 1 WHERE id = ?", intent);
                    lockHeld.countDown();
                    try {
                        if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Test did not release the held row lock in time.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
        try {
            Assertions.assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

            mvc.perform(post("/api/admin/media/upload-intents/complete")
                            .with(admin(owner))
                            .contentType("application/json")
                            .content("{\"intentIds\":[\"" + intent + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));

            assertThat(releaseLock.getCount()).isEqualTo(1);
            verify(storage, org.mockito.Mockito.never()).inspect(anyString(), any());
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void finalizationRowLockWaitConsumesBatchDeadlineAndDoesNotReturnReadyLate() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intent = UUID.fromString(create(owner));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> holder = new AtomicReference<>();
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> {
            holder.set(executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.update("UPDATE media_upload_intents SET version = version + 1 WHERE id = ?", intent);
                        lockHeld.countDown();
                        try {
                            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test did not release the held row lock in time.");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    })));
            Assertions.assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            return inspection(invocation.getArgument(0));
        });
        long startedAt = System.nanoTime();
        try {
            mvc.perform(post("/api/admin/media/upload-intents/complete")
                            .with(admin(owner))
                            .contentType("application/json")
                            .content("{\"intentIds\":[\"" + intent + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets", Integer.class))
                    .isZero();
            verify(storage).inspect(anyString(), any());
        } finally {
            releaseLock.countDown();
            if (holder.get() != null) holder.get().get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void providerFailureRetryLockWaitConsumesBatchDeadlineAndKeepsLease() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intent = UUID.fromString(create(owner));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> holder = new AtomicReference<>();
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> {
            holder.set(executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.update("UPDATE media_upload_intents SET version = version + 1 WHERE id = ?", intent);
                        lockHeld.countDown();
                        try {
                            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test did not release the held row lock in time.");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    })));
            Assertions.assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            throw new IllegalStateException("Simulated provider inspection failure.");
        });
        long startedAt = System.nanoTime();
        try {
            mvc.perform(post("/api/admin/media/upload-intents/complete")
                            .with(admin(owner))
                            .contentType("application/json")
                            .content("{\"intentIds\":[\"" + intent + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
            assertThat(jdbc.queryForObject(
                            "SELECT lease_until > CURRENT_TIMESTAMP FROM media_upload_intents WHERE id = ?",
                            Boolean.class,
                            intent))
                    .isTrue();
            verify(storage).inspect(anyString(), any());
        } finally {
            releaseLock.countDown();
            if (holder.get() != null) holder.get().get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void rejectionLockWaitConsumesBatchDeadlineAndKeepsLease() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intent = UUID.fromString(create(owner));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> holder = new AtomicReference<>();
        when(storage.inspect(anyString(), any())).thenAnswer(invocation -> {
            holder.set(executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.update("UPDATE media_upload_intents SET version = version + 1 WHERE id = ?", intent);
                        lockHeld.countDown();
                        try {
                            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test did not release the held row lock in time.");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    })));
            Assertions.assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            TrustedProviderInspection valid = inspection(invocation.getArgument(0));
            return new TrustedProviderInspection(
                    valid.assetId(),
                    valid.publicId(),
                    "raw",
                    valid.deliveryType(),
                    valid.format(),
                    valid.finalBytes(),
                    valid.finalWidth(),
                    valid.finalHeight(),
                    valid.policyVersion(),
                    valid.intentId(),
                    valid.sourceBytes(),
                    valid.sourceWidth(),
                    valid.sourceHeight(),
                    valid.sourceFormat(),
                    valid.sourceEvidence());
        });
        long startedAt = System.nanoTime();
        try {
            mvc.perform(post("/api/admin/media/upload-intents/complete")
                            .with(admin(owner))
                            .contentType("application/json")
                            .content("{\"intentIds\":[\"" + intent + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
            assertThat(jdbc.queryForObject(
                            "SELECT lease_until > CURRENT_TIMESTAMP FROM media_upload_intents WHERE id = ?",
                            Boolean.class,
                            intent))
                    .isTrue();
            verify(storage).inspect(anyString(), any());
        } finally {
            releaseLock.countDown();
            if (holder.get() != null) holder.get().get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private String create(UUID owner) throws Exception {
        MvcResult result = mvc.perform(
                        post("/api/admin/media/upload-intents")
                                .with(admin(owner))
                                .contentType("application/json")
                                .content(
                                        "{\"files\":[{\"clientFileId\":\"file-1\",\"filename\":\"image.png\",\"contentType\":\"image/png\",\"sizeBytes\":1000}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        return JsonPathSupport.readString(result, "$.items[0].intentId", json);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID subject) {
        return jwt().jwt(token -> token.subject(subject.toString())
                .claim("scope", "media.api")
                .claim("permissions", List.of("media:upload"))
                .claim("roles", List.of("ADMIN")));
    }

    private static TrustedProviderInspection inspection(String publicId) {
        UUID intentId = UUID.fromString(publicId.substring(publicId.lastIndexOf('/') + 1));
        return new TrustedProviderInspection(
                "asset-" + intentId,
                publicId,
                "image",
                "authenticated",
                "png",
                800L,
                10,
                10,
                "direct-a1-v1",
                intentId,
                900L,
                20,
                15,
                "png",
                "direct_intent=" + intentId);
    }

    private record InstantPair(java.time.Instant nextAttemptAt, java.time.Instant expiresAt) {}

    @TestConfiguration
    static class StorageFixtureConfiguration {
        @Bean
        @Primary
        DirectUploadStorage directUploadStorageFixture() {
            return org.mockito.Mockito.mock(DirectUploadStorage.class);
        }
    }

    private static final class JsonPathSupport {
        private JsonPathSupport() {}

        static String readString(MvcResult result, String path, JsonMapper json) throws Exception {
            String pointer = switch (path) {
                case "$.items[0].intentId" -> "/items/0/intentId";
                case "$.items[0].mediaId" -> "/items/0/mediaId";
                default -> throw new IllegalArgumentException("Unsupported response field: " + path);
            };
            return json.readTree(result.getResponse().getContentAsString())
                    .at(pointer)
                    .stringValue();
        }
    }
}
