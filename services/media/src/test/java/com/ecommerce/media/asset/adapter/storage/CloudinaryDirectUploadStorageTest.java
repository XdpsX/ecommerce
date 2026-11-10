package com.ecommerce.media.asset.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ecommerce.media.asset.application.model.DirectUploadPolicy;

class CloudinaryDirectUploadStorageTest {
    @Test
    void generatedEvalFailsClosedOnMissingOriginalMetadataAndAnimationEvidence() {
        UUID intentId = UUID.randomUUID();
        DirectUploadPolicy policy = new DirectUploadPolicy(
                intentId,
                "ecommerce-media/direct/" + intentId,
                "direct-a1-v1",
                5_242_880,
                10_000,
                20_000_000,
                Instant.parse("2026-11-06T00:00:00Z"),
                Instant.parse("2026-11-06T00:15:00Z"),
                "a_auto/fl_strip_profile",
                "");

        String eval = CloudinaryDirectUploadStorage.evaluation(policy);

        assertThat(eval)
                .contains("!i||!n(i.bytes)||!n(i.width)||!n(i.height)")
                .contains("i.width*i.height>20000000")
                .contains("function one(v){return v===undefined||n(v)&&v===1;}")
                .contains("!one(i.pages)||!one(i.frames)")
                .contains("i.is_animated!==undefined&&i.is_animated!==false")
                .contains("throw new Error('INPUT_POLICY_REJECTED')")
                .contains("throw new Error('UNSUPPORTED_INPUT')")
                .contains("source_width='+i.width")
                .contains("source_height='+i.height");
    }

    @Test
    void providerRequestSignatureContainsOnlyTheServerGeneratedEvalAndCallback() {
        UUID intentId = UUID.randomUUID();
        DirectUploadPolicy policy = new DirectUploadPolicy(
                intentId,
                "ecommerce-media/direct/" + intentId,
                "direct-a1-v1",
                1_000_000,
                10_000,
                20_000_000,
                Instant.parse("2026-11-06T00:00:00Z"),
                Instant.parse("2026-11-06T00:15:00Z"),
                "a_auto/fl_strip_profile",
                "https://media.example.test/api/media/cloudinary/notifications");
        com.ecommerce.media.config.MediaProperties properties = new com.ecommerce.media.config.MediaProperties();
        properties.getStorage().getCloudinary().setCloudName("fixture-cloud");
        properties.getStorage().getCloudinary().setApiKey("fixture-key");
        properties.getStorage().getCloudinary().setApiSecret("fixture-secret");
        CloudinaryDirectUploadStorage storage = new CloudinaryDirectUploadStorage(
                new com.cloudinary.Cloudinary(java.util.Map.of(
                        "cloud_name", "fixture-cloud",
                        "api_key", "fixture-key",
                        "api_secret", "fixture-secret",
                        "secure", true)),
                properties);

        try {
            var signed = storage.sign(policy);
            assertThat(signed.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/fixture-cloud/image/upload");
            assertThat(signed.fields())
                    .containsEntry("public_id", policy.publicId())
                    .containsEntry("type", "authenticated")
                    .containsEntry("overwrite", "false")
                    .containsEntry("notification_url", policy.notificationUrl())
                    .containsEntry("eval", CloudinaryDirectUploadStorage.evaluation(policy))
                    .containsKeys("api_key", "signature");
        } finally {
            storage.close();
        }
    }
}
