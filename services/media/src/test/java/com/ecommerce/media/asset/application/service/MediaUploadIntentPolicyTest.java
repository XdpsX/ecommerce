package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ecommerce.media.asset.application.model.DirectUploadSettings;
import com.ecommerce.media.asset.application.model.ImagePolicy;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.model.UploadIntentOutcome.ErrorCode;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;

class MediaUploadIntentPolicyTest {
    private final DirectUploadSettings settings = new DirectUploadSettings(
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
            3,
            100,
            10,
            10,
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofHours(1),
            200,
            Duration.ofHours(24));
    private final ImagePolicy imagePolicy = new ImagePolicy(5_242_880, 4_194_304, 20_000_000, 10_000);
    private final MediaUploadIntentService service = new MediaUploadIntentService(
            null,
            null,
            null,
            settings,
            imagePolicy,
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void rejectsFinalImageWhenTrustedOriginalDimensionsAreUnavailable() {
        UUID intentId = UUID.randomUUID();
        MediaUploadIntent intent = intent(intentId, "image/png", 1_000_000);
        TrustedProviderInspection inspection = new TrustedProviderInspection(
                "asset-1",
                intent.providerPublicId(),
                "image",
                "authenticated",
                "png",
                100_000L,
                100,
                100,
                MediaUploadIntentService.POLICY_VERSION,
                intentId,
                900_000L,
                null,
                null,
                "png",
                "direct_intent=" + intentId);

        assertThat(service.validateInspection(intent, inspection, settings)).isEqualTo(ErrorCode.INPUT_POLICY_REJECTED);
    }

    @Test
    void rejectsOriginalFormatThatDoesNotMatchTheDeclaredImageType() {
        UUID intentId = UUID.randomUUID();
        MediaUploadIntent intent = intent(intentId, "image/png", 1_000_000);
        TrustedProviderInspection inspection = new TrustedProviderInspection(
                "asset-1",
                intent.providerPublicId(),
                "image",
                "authenticated",
                "png",
                100_000L,
                100,
                100,
                MediaUploadIntentService.POLICY_VERSION,
                intentId,
                900_000L,
                200,
                100,
                "jpeg",
                "direct_intent=" + intentId);

        assertThat(service.validateInspection(intent, inspection, settings)).isEqualTo(ErrorCode.INPUT_POLICY_REJECTED);
    }

    @Test
    void rejectsTrustedFinalOutputAboveTheConfiguredCap() {
        UUID intentId = UUID.randomUUID();
        MediaUploadIntent intent = intent(intentId, "image/png", 1_000_000);
        TrustedProviderInspection inspection = new TrustedProviderInspection(
                "asset-1",
                intent.providerPublicId(),
                "image",
                "authenticated",
                "png",
                4_194_305L,
                100,
                100,
                MediaUploadIntentService.POLICY_VERSION,
                intentId,
                900_000L,
                200,
                150,
                "png",
                "direct_intent=" + intentId);

        assertThat(service.validateInspection(intent, inspection, settings)).isEqualTo(ErrorCode.OUTPUT_LIMIT_EXCEEDED);
    }

    private static MediaUploadIntent intent(UUID id, String declaredType, long declaredBytes) {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        return new MediaUploadIntent(
                id,
                UUID.randomUUID(),
                "file-1",
                "image.png",
                declaredType,
                declaredBytes,
                MediaUploadIntentService.POLICY_VERSION,
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
                now,
                now.plusSeconds(3600),
                null,
                "image",
                null,
                null,
                0);
    }
}
