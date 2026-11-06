package com.ecommerce.media.asset.adapter.web;

import java.io.IOException;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.media.adapter.web.ApiProblems;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.service.MediaUploadIntentService;
import com.ecommerce.media.configuration.MediaProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class CloudinaryNotificationController {
    private final DirectUploadStorage storage;
    private final MediaUploadIntentService intents;
    private final MediaProperties.DirectUpload properties;
    private final JsonMapper json;
    private final ApiProblems problems;
    private final Clock clock;

    public CloudinaryNotificationController(
            DirectUploadStorage storage,
            MediaUploadIntentService intents,
            MediaProperties mediaProperties,
            JsonMapper json,
            ApiProblems problems,
            Clock mediaClock) {
        this.storage = storage;
        this.intents = intents;
        this.properties = mediaProperties.getDirectUpload();
        this.json = json;
        this.problems = problems;
        this.clock = mediaClock;
    }

    @PostMapping(path = "/api/media/cloudinary/notifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> notify(
            HttpServletRequest request,
            @RequestHeader(name = "X-Cld-Timestamp", required = false) String timestamp,
            @RequestHeader(name = "X-Cld-Signature", required = false) String signature) {
        if (!properties.isWebhookEnabled() || properties.getNotificationApiKey().isBlank()) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", request);
        }
        byte[] body;
        try {
            body = request.getInputStream().readNBytes(properties.getMaxNotificationBytes() + 1);
        } catch (IOException exception) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
        }
        if (body.length > properties.getMaxNotificationBytes()) {
            return problem(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", request);
        }
        if (timestamp == null
                || signature == null
                || !timestamp.matches("[0-9]{1,12}")
                || !signature.matches("[A-Fa-f0-9]{64}")) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", request);
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", request);
        }
        long now = clock.instant().getEpochSecond();
        if (issuedAt < now - properties.getNotificationMaxAge().toSeconds()
                || issuedAt > now + properties.getNotificationFutureSkew().toSeconds()) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", request);
        }
        String raw = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        if (!storage.verifyNotification(
                raw, timestamp, signature, properties.getNotificationMaxAge().toSeconds())) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", request);
        }
        JsonNode payload;
        try {
            payload = json.readTree(body);
        } catch (Exception exception) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
        }
        if (payload == null || !payload.isObject()) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
        }
        String resourceType = text(payload, "resource_type");
        String deliveryType = text(payload, "type");
        String publicId = text(payload, "public_id");
        if ("image".equals(resourceType)
                && "authenticated".equals(deliveryType)
                && publicId != null
                && publicId.length() <= 512) {
            intents.scheduleInspection(publicId);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, HttpServletRequest request) {
        ProblemDetail detail =
                ApiProblems.create(status, code, "The notification could not be accepted.", request.getRequestURI());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(detail);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
