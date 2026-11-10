package com.ecommerce.media.asset.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.service.MediaUploadIntentService;
import com.ecommerce.media.config.MediaProperties;
import com.ecommerce.media.shared.error.ApiProblems;

import tools.jackson.databind.json.JsonMapper;

class CloudinaryNotificationControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void verifiesAndSchedulesFromTheOriginalRawBodyOnly() {
        DirectUploadStorage storage = mock(DirectUploadStorage.class);
        MediaUploadIntentService intents = mock(MediaUploadIntentService.class);
        String publicId = "ecommerce-media/direct/" + UUID.randomUUID();
        String rawBody =
                "{ \"resource_type\": \"image\", \"type\": \"authenticated\", \"public_id\": \"" + publicId + "\" }";
        String timestamp = Long.toString(NOW.getEpochSecond());
        when(storage.verifyNotification(rawBody, timestamp, SIGNATURE, 300)).thenReturn(true);
        CloudinaryNotificationController controller = controller(storage, intents, 65_536);
        MockHttpServletRequest request = request(rawBody);

        var response = controller.notify(request, timestamp, SIGNATURE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(storage).verifyNotification(rawBody, timestamp, SIGNATURE, 300);
        verify(intents).scheduleInspection(eq(publicId));
    }

    @Test
    void rejectsInvalidSignatureWithoutSchedulingAnIntent() {
        DirectUploadStorage storage = mock(DirectUploadStorage.class);
        MediaUploadIntentService intents = mock(MediaUploadIntentService.class);
        String body = "{\"resource_type\":\"image\",\"type\":\"authenticated\",\"public_id\":\"x\"}";
        String timestamp = Long.toString(NOW.getEpochSecond());
        when(storage.verifyNotification(body, timestamp, SIGNATURE, 300)).thenReturn(false);

        var response = controller(storage, intents, 65_536).notify(request(body), timestamp, SIGNATURE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(storage).verifyNotification(body, timestamp, SIGNATURE, 300);
        verify(intents, never()).scheduleInspection(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void enforcesBodyLimitAndReturnsRetryableFailureWhenSchedulingCannotCommit() {
        DirectUploadStorage oversizedStorage = mock(DirectUploadStorage.class);
        MediaUploadIntentService oversizedIntents = mock(MediaUploadIntentService.class);
        String body = "{ \"resource_type\": \"image\" }";
        String timestamp = Long.toString(NOW.getEpochSecond());
        var tooLarge = controller(oversizedStorage, oversizedIntents, 8).notify(request(body), timestamp, "signature");
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

        DirectUploadStorage storage = mock(DirectUploadStorage.class);
        MediaUploadIntentService intents = mock(MediaUploadIntentService.class);
        String publicId = "ecommerce-media/direct/" + UUID.randomUUID();
        String event = "{\"resource_type\":\"image\",\"type\":\"authenticated\",\"public_id\":\"" + publicId + "\"}";
        when(storage.verifyNotification(event, timestamp, SIGNATURE, 300)).thenReturn(true);
        when(intents.scheduleInspection(publicId)).thenThrow(new DataAccessResourceFailureException("fixture"));

        assertThatThrownBy(() -> controller(storage, intents, 65_536).notify(request(event), timestamp, SIGNATURE))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private static CloudinaryNotificationController controller(
            DirectUploadStorage storage, MediaUploadIntentService intents, int bodyLimit) {
        MediaProperties properties = new MediaProperties();
        properties.getDirectUpload().setWebhookEnabled(true);
        properties.getDirectUpload().setNotificationApiKey("fixture-notification-key");
        properties.getDirectUpload().setMaxNotificationBytes(bodyLimit);
        JsonMapper json = JsonMapper.builder().build();
        return new CloudinaryNotificationController(storage, intents, properties, json, new ApiProblems(json), CLOCK);
    }

    private static MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/cloudinary/notifications");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
