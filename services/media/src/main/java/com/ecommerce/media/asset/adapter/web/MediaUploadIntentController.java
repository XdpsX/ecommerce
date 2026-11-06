package com.ecommerce.media.asset.adapter.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.media.asset.application.model.DirectUploadBatch;
import com.ecommerce.media.asset.application.model.UploadIntentBatchResult;
import com.ecommerce.media.asset.application.model.UploadIntentDeclaration;
import com.ecommerce.media.asset.application.service.MediaUploadIntentService;

@RestController
@RequestMapping("/api/admin/media/upload-intents")
public class MediaUploadIntentController {
    private final MediaUploadIntentService service;

    public MediaUploadIntentController(MediaUploadIntentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DirectUploadBatch> create(
            @Valid @RequestBody DirectUploadRequests.CreateBatch request, @AuthenticationPrincipal Jwt jwt) {
        var files = request.files().stream()
                .map(file -> new UploadIntentDeclaration(
                        file.clientFileId(), file.filename(), file.contentType(), file.sizeBytes()))
                .toList();
        DirectUploadBatch result = service.createBatch(UUID.fromString(jwt.getSubject()), files);
        return ResponseEntity.created(URI.create("/api/admin/media/upload-intents"))
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @PostMapping("/complete")
    public ResponseEntity<UploadIntentBatchResult> complete(
            @Valid @RequestBody DirectUploadRequests.IntentIds request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.completeBatch(UUID.fromString(jwt.getSubject()), request.intentIds()));
    }

    @PostMapping("/status")
    public ResponseEntity<UploadIntentBatchResult> status(
            @Valid @RequestBody DirectUploadRequests.IntentIds request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getStatuses(UUID.fromString(jwt.getSubject()), request.intentIds()));
    }
}
