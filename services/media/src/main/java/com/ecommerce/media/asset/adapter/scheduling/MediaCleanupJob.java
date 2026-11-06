package com.ecommerce.media.asset.adapter.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.media.asset.application.service.MediaAssetService;

@Component
@ConditionalOnProperty(prefix = "media.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupJob {
    private static final Logger logger = LoggerFactory.getLogger(MediaCleanupJob.class);
    private final MediaAssetService service;

    public MediaCleanupJob(MediaAssetService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.cleanup.interval:PT1M}")
    public void cleanDueAssets() {
        MediaAssetService.CleanupSummary summary = service.runCleanupBatch();
        if (summary.claimed() > 0) {
            logger.info(
                    "Media cleanup batch claimed={} deleted={} retried={} ambiguous={}",
                    summary.claimed(),
                    summary.deleted(),
                    summary.retried(),
                    summary.ambiguous());
        }
    }
}
