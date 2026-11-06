package com.ecommerce.media.asset.adapter.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.media.asset.application.service.MediaUploadRecoveryService;

@Component
@ConditionalOnProperty(prefix = "media.direct-upload", name = "enabled", havingValue = "true")
public class MediaUploadRecoveryJob {
    private final MediaUploadRecoveryService service;

    public MediaUploadRecoveryJob(MediaUploadRecoveryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.direct-upload.reconcile-interval:PT1M}")
    public void recoverDueDirectUploads() {
        service.runBatch();
    }
}
