package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.model.UploadIntentOutcome.ErrorCode;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;
import com.ecommerce.media.configuration.MediaProperties;

class MediaUploadIntentPolicyTest {
    private final MediaProperties properties = new MediaProperties();
    private final MediaUploadIntentService service = new MediaUploadIntentService(
            null, null, null, properties, Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

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

        assertThat(service.validateInspection(intent, inspection, properties.getDirectUpload()))
                .isEqualTo(ErrorCode.INPUT_POLICY_REJECTED);
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

        assertThat(service.validateInspection(intent, inspection, properties.getDirectUpload()))
                .isEqualTo(ErrorCode.INPUT_POLICY_REJECTED);
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

        assertThat(service.validateInspection(intent, inspection, properties.getDirectUpload()))
                .isEqualTo(ErrorCode.OUTPUT_LIMIT_EXCEEDED);
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
