package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.DiscoveryPage;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;
import com.ecommerce.media.configuration.MediaProperties;

class MediaUploadRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private MediaUploadIntentRepository repository;
    private DirectUploadStorage storage;
    private MediaProperties properties;
    private MediaUploadIntent intent;
    private MediaUploadRecoveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(MediaUploadIntentRepository.class);
        storage = mock(DirectUploadStorage.class);
        properties = new MediaProperties();
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
        service = new MediaUploadRecoveryService(
                repository,
                mock(MediaUploadIntentService.class),
                storage,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        properties.getDirectUpload().setDiscoveryPagesPerClaim(1);

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
