package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ecommerce.media.asset.application.model.DirectUploadSettings;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.DiscoveryPage;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;

class MediaUploadRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private MediaUploadIntentRepository repository;
    private DirectUploadStorage storage;
    private DirectUploadSettings settings;
    private MediaUploadIntent intent;
    private MediaUploadRecoveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(MediaUploadIntentRepository.class);
        storage = mock(DirectUploadStorage.class);
        settings = directUploadSettings(3);
        intent = intent(NOW);
        when(repository.claimDue(any(), any(), any(), anyInt())).thenReturn(List.of(intent));
        when(repository.reserveProviderCall(any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(true);
        when(storage.discover(anyString(), anyString(), any(), anyInt()))
                .thenReturn(new DiscoveryPage(List.of(), null));
        when(repository.claimDiscoveredTargets(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(repository.issuanceEnabled()).thenReturn(true);
        when(repository.cleanupBacklog()).thenReturn(0L);
        service = createService(settings);
    }

    @Test
    void schedulesCompletedEmptySweepAtNormalDiscoveryInterval() {
        service.runBatch();

        ArgumentCaptor<Instant> nextDue = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(repository)
                .saveDiscoveryProgress(
                        org.mockito.ArgumentMatchers.eq(intent.id()),
                        any(),
                        org.mockito.ArgumentMatchers.eq("image"),
                        org.mockito.ArgumentMatchers.isNull(),
                        nextDue.capture(),
                        org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(nextDue.getValue()).isEqualTo(NOW.plusSeconds(3_600));
    }

    @Test
    void schedulesPageBudgetContinuationImmediately() {
        settings = directUploadSettings(1);
        service = createService(settings);

        service.runBatch();

        ArgumentCaptor<Instant> nextDue = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(repository)
                .saveDiscoveryProgress(
                        org.mockito.ArgumentMatchers.eq(intent.id()),
                        any(),
                        org.mockito.ArgumentMatchers.eq("raw"),
                        org.mockito.ArgumentMatchers.isNull(),
                        nextDue.capture(),
                        org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(nextDue.getValue()).isEqualTo(NOW);
    }

    private MediaUploadRecoveryService createService(DirectUploadSettings settings) {
        return new MediaUploadRecoveryService(
                repository, mock(MediaUploadIntentService.class), storage, settings, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DirectUploadSettings directUploadSettings(int discoveryPagesPerClaim) {
        return new DirectUploadSettings(
                true,
                true,
                false,
                false,
                "",
                "ecommerce-media",
                10,
                5_242_880,
                52_428_800,
                100,
                1_000,
                20,
                200,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofMinutes(15),
                120,
                30,
                discoveryPagesPerClaim,
                100,
                10,
                10,
                Duration.ofMinutes(1),
                Duration.ofMinutes(2),
                Duration.ofHours(1),
                200,
                Duration.ofHours(24));
    }

    private static MediaUploadIntent intent(Instant now) {
        UUID id = UUID.randomUUID();
        return new MediaUploadIntent(
                id,
                UUID.randomUUID(),
                "file-1",
                "image.png",
                "image/png",
                1_000,
                "direct-a1-v1",
                "media/direct/" + id,
                UploadIntentState.EXPIRED,
                now.minusSeconds(3_600),
                now.minusSeconds(3_600),
                now.minusSeconds(2_700),
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
                "EXPIRED",
                null,
                0,
                now,
                now,
                null,
                "image",
                null,
                null,
                0);
    }
}
