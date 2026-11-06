package com.ecommerce.media.asset.application.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.ecommerce.media.asset.application.exception.MediaException;
import com.ecommerce.media.asset.application.exception.StorageFailure;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.asset.application.model.DownloadLink;
import com.ecommerce.media.asset.application.model.MediaAssetView;
import com.ecommerce.media.asset.application.port.MediaAssetRepository;
import com.ecommerce.media.asset.application.port.MediaStorage;
import com.ecommerce.media.asset.domain.AssetStatus;
import com.ecommerce.media.asset.domain.MediaAsset;
import com.ecommerce.media.configuration.MediaProperties;

@Service
public class MediaAssetService {
    private final MediaAssetRepository repository;
    private final ImageCanonicalizer canonicalizer;
    private final MediaStorage storage;
    private final MediaProperties properties;
    private final Clock clock;

    public MediaAssetService(
            MediaAssetRepository repository,
            ImageCanonicalizer canonicalizer,
            MediaStorage storage,
            MediaProperties properties,
            Clock mediaClock) {
        this.repository = repository;
        this.canonicalizer = canonicalizer;
        this.storage = storage;
        this.properties = properties;
        this.clock = mediaClock;
    }

    public MediaAssetView upload(UUID uploaderId, java.io.InputStream input) throws IOException {
        CanonicalImage image = canonicalizer.canonicalize(input);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String publicId = storage.allocateObjectKey(id);
        MediaAsset uploading = new MediaAsset(
                id,
                uploaderId,
                AssetStatus.UPLOADING,
                image.mediaType(),
                image.bytes().length,
                image.width(),
                image.height(),
                publicId,
                null,
                now,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                0);
        repository.createUploading(uploading);

        MediaStorage.StoredAsset stored;
        try {
            MediaStorage.StoredAsset uploadResponse = storage.upload(publicId, image);
            MediaStorage.StoredAsset inspected = storage.inspect(publicId);
            stored = verified(uploadResponse, inspected, publicId, image);
        } catch (RuntimeException exception) {
            repository.markAmbiguousUpload(id, storageCategory(exception), retryAt(1), clock.instant());
            throw MediaException.unavailable();
        }
        Instant completedAt = clock.instant();
        if (stored == null) {
            repository.markAmbiguousUpload(id, "PROVIDER_IDENTITY_MISMATCH", retryAt(1), completedAt);
            throw MediaException.unavailable();
        }
        if (!repository.markReady(id, stored.assetId(), completedAt)) {
            repository.markAmbiguousUpload(id, "UPLOAD_COMPLETED_AFTER_CLEANUP_CLAIM", clock.instant(), completedAt);
            throw MediaException.unavailable();
        }
        return repository.find(id).map(MediaAssetView::from).orElseThrow(MediaException::unavailable);
    }

    public MediaAssetView get(UUID id) {
        MediaAsset asset = repository.find(id).orElseThrow(() -> notFound());
        if (asset.status() != AssetStatus.READY) {
            throw notFound();
        }
        return MediaAssetView.from(asset);
    }

    public DownloadLink downloadLink(UUID id) {
        MediaAsset asset = repository.find(id).orElseThrow(() -> notFound());
        if (asset.status() == AssetStatus.DELETED) {
            throw notFound();
        }
        if (asset.status() != AssetStatus.READY) {
            throw MediaException.conflict();
        }
        Instant expiry = Instant.ofEpochSecond(
                clock.instant().plus(properties.getDownloadLinkTtl()).getEpochSecond());
        try {
            String url = storage.downloadLink(asset.providerPublicId(), format(asset.mediaType()), expiry);
            return new DownloadLink(url, expiry);
        } catch (RuntimeException exception) {
            throw MediaException.unavailable();
        }
    }

    public void requestDelete(UUID id) {
        MediaAsset asset = repository.find(id).orElseThrow(() -> notFound());
        if (asset.status() == AssetStatus.DELETED || asset.status() == AssetStatus.DELETE_PENDING) {
            return;
        }
        if (asset.status() != AssetStatus.READY) {
            throw MediaException.conflict();
        }
        if (!repository.queueDeletion(id, clock.instant())) {
            MediaAsset latest = repository.find(id).orElseThrow(() -> notFound());
            if (latest.status() == AssetStatus.DELETE_PENDING || latest.status() == AssetStatus.DELETED) {
                return;
            }
            throw MediaException.conflict();
        }
    }

    public CleanupSummary runCleanupBatch() {
        Instant now = clock.instant();
        MediaProperties.Cleanup cleanup = properties.getCleanup();
        Instant abandonedBefore = now.minus(cleanup.getAbandonAfter());
        int deleted = 0;
        int retried = 0;
        int ambiguousCount = 0;
        int claimedCount = 0;
        for (int index = 0; index < cleanup.getBatchSize(); index++) {
            List<MediaAsset> assets = repository.claimCleanup(
                    clock.instant(), abandonedBefore, clock.instant().plus(cleanup.getLeaseDuration()), 1);
            if (assets.isEmpty()) {
                break;
            }
            MediaAsset claimed = assets.getFirst();
            claimedCount++;
            boolean ambiguous = claimed.status() == AssetStatus.AMBIGUOUS_UPLOAD;
            try {
                MediaStorage.DeleteResult result = storage.delete(claimed.providerPublicId());
                Instant completed = clock.instant();
                if (ambiguous) {
                    repository.cleanupAmbiguousPending(
                            claimed.id(),
                            claimed.leaseId(),
                            result == MediaStorage.DeleteResult.NOT_FOUND
                                    ? "PROVIDER_NOT_FOUND_PENDING_RECONCILIATION"
                                    : "UPLOAD_MAY_STILL_COMPLETE_AFTER_DELETE",
                            retryAt(claimed.cleanupAttempts()),
                            completed);
                    ambiguousCount++;
                } else {
                    repository.cleanupDeleted(claimed.id(), claimed.leaseId(), completed);
                    deleted++;
                }
            } catch (RuntimeException exception) {
                repository.cleanupFailed(
                        claimed.id(),
                        claimed.leaseId(),
                        ambiguous,
                        storageCategory(exception),
                        retryAt(claimed.cleanupAttempts()),
                        clock.instant());
                retried++;
            }
        }
        return new CleanupSummary(claimedCount, deleted, retried, ambiguousCount);
    }

    private Instant retryAt(int attempt) {
        long exponent = Math.min(Math.max(0, attempt - 1), 20);
        long cap = Math.max(1, properties.getCleanup().getRetryCap().toSeconds());
        long ceiling = Math.min(cap, 60L << exponent);
        long seconds = ThreadLocalRandom.current().nextLong(Math.max(1, ceiling / 2), ceiling + 1);
        return clock.instant().plusSeconds(seconds);
    }

    private String storageCategory(RuntimeException exception) {
        return exception instanceof StorageFailure failure
                ? failure.category().name()
                : StorageFailure.Category.OPERATION_FAILED.name();
    }

    private MediaStorage.StoredAsset verified(
            MediaStorage.StoredAsset uploaded,
            MediaStorage.StoredAsset inspected,
            String publicId,
            CanonicalImage image) {
        if (uploaded == null
                || inspected == null
                || !publicId.equals(uploaded.publicId())
                || !publicId.equals(inspected.publicId())
                || !uploaded.assetId().equals(inspected.assetId())
                || !"image".equals(inspected.resourceType())
                || !"authenticated".equals(inspected.deliveryType())
                || !image.format().equals(inspected.format())) {
            return null;
        }
        return inspected;
    }

    private String format(String mediaType) {
        return mediaType.equals("image/jpeg") ? "jpg" : "png";
    }

    private MediaException notFound() {
        return MediaException.notFound();
    }

    public record CleanupSummary(int claimed, int deleted, int retried, int ambiguous) {}
}
