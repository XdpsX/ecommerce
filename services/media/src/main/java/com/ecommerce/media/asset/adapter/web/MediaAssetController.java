package com.ecommerce.media.asset.adapter.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommerce.media.asset.application.model.DownloadLink;
import com.ecommerce.media.asset.application.model.MediaAssetView;
import com.ecommerce.media.asset.application.service.MediaAssetService;

@RestController
@RequestMapping("/api/admin/media")
public class MediaAssetController {
    private final MediaAssetService service;

    public MediaAssetController(MediaAssetService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAssetView> upload(
            @RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) throws IOException {
        UUID uploader = UUID.fromString(jwt.getSubject());
        MediaAssetView view;
        try (InputStream input = file.getInputStream()) {
            view = service.upload(uploader, input);
        }
        return ResponseEntity.created(URI.create("/api/admin/media/" + view.id()))
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaAssetView> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }

    @PostMapping("/{id}/download-link")
    public ResponseEntity<DownloadLink> downloadLink(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.downloadLink(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.requestDelete(id);
        return ResponseEntity.accepted()
                .location(URI.create("/api/admin/media/" + id))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
