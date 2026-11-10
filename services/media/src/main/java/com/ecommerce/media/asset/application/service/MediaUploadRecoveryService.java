package com.ecommerce.media.asset.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.media.asset.application.exception.DirectUploadException;
import com.ecommerce.media.asset.application.exception.MediaPersistenceLockTimeoutException;
import com.ecommerce.media.asset.application.model.DirectUploadSettings;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.port.DirectUploadStorage.DiscoveredObject;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository.DirectTarget;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository.DiscoveredTarget;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;

@Service
public class MediaUploadRecoveryService {
    private static final Logger logger = LoggerFactory.getLogger(MediaUploadRecoveryService.class);
    private static final List<String> RESOURCE_TYPES = List.of("image", "raw", "video");
    private final MediaUploadIntentRepository repository;
    private final MediaUploadIntentService verifier;
    private final DirectUploadStorage storage;
    private final DirectUploadSettings settings;
    private final Clock clock;

    public MediaUploadRecoveryService(
            MediaUploadIntentRepository repository,
            MediaUploadIntentService verifier,
            DirectUploadStorage storage,
            DirectUploadSettings settings,
            Clock mediaClock) {
        this.repository = repository;
        this.verifier = verifier;
        this.storage = storage;
        this.settings = settings;
        this.clock = mediaClock;
    }

    public void runBatch() {
        Instant now = clock.instant();
        UUID lease = UUID.randomUUID();
        List<MediaUploadIntent> intents =
                repository.claimDue(now, lease, now.plus(settings.leaseDuration()), settings.reconcileBatchSize());
        int failed = 0;
        for (MediaUploadIntent intent : intents) {
            try {
                recover(intent, lease, now);
            } catch (RuntimeException exception) {
                failed++;
                logger.warn("Direct upload recovery item failed; category={}", category(exception));
                try {
                    repository.retry(
                            intent.id(),
                            lease,
                            category(exception),
                            clock.instant().plus(retryDelay(intent.retryCount())),
                            clock.instant(),
                            settings.batchDeadline());
                } catch (MediaPersistenceLockTimeoutException retryFailure) {
                    logger.warn("Could not persist recovery retry before lock timeout; lease retained");
                }
            }
        }
        if (!intents.isEmpty() || !repository.issuanceEnabled()) {
            Instant oldestDue = repository.oldestRecoveryDueAt();
            long oldestDueAgeSeconds = oldestDue == null
                    ? 0
                    : Math.max(0, Duration.between(oldestDue, now).toSeconds());
            logger.info(
                    "Direct upload recovery batch claimed={} failed={} backlog={} issuanceEnabled={} oldestDueAgeSeconds={}",
                    intents.size(),
                    failed,
                    repository.cleanupBacklog(),
                    repository.issuanceEnabled(),
                    oldestDueAgeSeconds);
        }
    }

    private void recover(MediaUploadIntent intent, UUID lease, Instant now) {
        if (intent.state() == UploadIntentState.PENDING) {
            verifier.reconcilePending(intent, lease, settings, now);
            return;
        }
        if (intent.state() == UploadIntentState.COMPLETED
                && (intent.mediaId() == null || intent.providerAssetId() == null)) {
            repository.saveDiscoveryProgress(intent.id(), lease, "image", null, now.plus(settings.retryCap()), now);
            return;
        }
        discoverAndDelete(intent, lease, now);
    }

    private void discoverAndDelete(MediaUploadIntent intent, UUID lease, Instant now) {
        String resourceType = validResourceType(intent.discoveryResourceType());
        String cursor = intent.discoveryCursor();
        String nextType = resourceType;
        String nextCursor = cursor;
        Instant nextDue = now.plus(discoveryInterval(intent, now));
        for (int page = 0; page < settings.discoveryPagesPerClaim(); page++) {
            if (!reserveRecoveryCall(now)) {
                nextDue = nextBudgetReset(now);
                break;
            }
            var response =
                    storage.discover(intent.providerPublicId(), resourceType, cursor, settings.discoveryPageSize());
            List<DirectTarget> safeTargets = response.objects().stream()
                    .filter(found -> trustedExactIdentity(intent, found))
                    .filter(found -> !isReadyWinner(intent, found))
                    .map(found -> new DirectTarget(
                            found.resourceType(), found.deliveryType(), found.publicId(), found.assetId()))
                    .toList();
            repository.saveDiscoveredTargets(intent.id(), safeTargets, now);
            if (response.nextCursor() != null && !response.nextCursor().isBlank()) {
                cursor = response.nextCursor();
                nextCursor = cursor;
                nextType = resourceType;
                nextDue = now;
                continue;
            }
            int resourceIndex = RESOURCE_TYPES.indexOf(resourceType);
            if (resourceIndex + 1 < RESOURCE_TYPES.size()) {
                resourceType = RESOURCE_TYPES.get(resourceIndex + 1);
                cursor = null;
                nextType = resourceType;
                nextCursor = null;
                nextDue = now;
                continue;
            }
            nextType = "image";
            nextCursor = null;
            nextDue = now.plus(discoveryInterval(intent, now));
            break;
        }
        repository.saveDiscoveryProgress(intent.id(), lease, nextType, nextCursor, nextDue, clock.instant());
        deleteTargets(intent, lease, now);
    }

    private void deleteTargets(MediaUploadIntent intent, UUID leaseToken, Instant now) {
        List<DiscoveredTarget> targets = repository.claimDiscoveredTargets(
                intent.id(), leaseToken, now, now.plus(settings.leaseDuration()), settings.deletesPerClaim());
        for (DiscoveredTarget target : targets) {
            if (!safeStoredTarget(intent, target)
                    || intent.state() == UploadIntentState.COMPLETED
                            && "image".equals(target.resourceType())
                            && intent.providerAssetId().equals(target.assetId())) {
                repository.retryTarget(
                        target.id(), leaseToken, "IDENTITY_OR_WINNER_PROTECTED", now.plus(settings.retryCap()), now);
                continue;
            }
            if (!reserveRecoveryCall(clock.instant())) {
                repository.retryTarget(
                        target.id(),
                        leaseToken,
                        "PROVIDER_BUDGET_EXHAUSTED",
                        nextBudgetReset(clock.instant()),
                        clock.instant());
                continue;
            }
            try {
                storage.delete(new DiscoveredObject(
                        target.resourceType(), target.deliveryType(), target.publicId(), target.assetId()));
                repository.markTargetDeleted(target.id(), leaseToken, clock.instant());
            } catch (RuntimeException exception) {
                repository.retryTarget(
                        target.id(),
                        leaseToken,
                        category(exception),
                        clock.instant().plus(retryDelay(target.attempts())),
                        clock.instant());
            }
        }
    }

    private boolean reserveRecoveryCall(Instant now) {
        Instant hour = now.atZone(ZoneOffset.UTC)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        return repository.reserveProviderCall(
                hour, true, settings.providerCallsPerHour(), settings.reservedRecoveryCallsPerHour());
    }

    private boolean trustedExactIdentity(MediaUploadIntent intent, DiscoveredObject found) {
        return "authenticated".equals(found.deliveryType())
                && validResourceType(found.resourceType()).equals(found.resourceType())
                && found.assetId() != null
                && exactGeneratedId(intent.providerPublicId(), found.publicId());
    }

    private boolean isReadyWinner(MediaUploadIntent intent, DiscoveredObject found) {
        return intent.state() == UploadIntentState.COMPLETED
                && "image".equals(found.resourceType())
                && intent.providerAssetId() != null
                && intent.providerAssetId().equals(found.assetId());
    }

    private boolean safeStoredTarget(MediaUploadIntent intent, DiscoveredTarget target) {
        return RESOURCE_TYPES.contains(target.resourceType())
                && "authenticated".equals(target.deliveryType())
                && target.assetId() != null
                && exactGeneratedId(intent.providerPublicId(), target.publicId());
    }

    private static boolean exactGeneratedId(String base, String candidate) {
        if (base.equals(candidate)) return true;
        if (candidate == null || !candidate.startsWith(base + ".")) return false;
        String suffix = candidate.substring(base.length() + 1);
        return !suffix.isBlank() && suffix.length() <= 32 && suffix.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");
    }

    private Duration discoveryInterval(MediaUploadIntent intent, Instant now) {
        Duration age = Duration.between(intent.createdAt(), now);
        return age.compareTo(Duration.ofHours(24)) < 0 ? Duration.ofHours(1) : Duration.ofDays(1);
    }

    private static String validResourceType(String value) {
        return RESOURCE_TYPES.contains(value) ? value : "image";
    }

    private Duration retryDelay(int attempts) {
        long baseSeconds = Math.min(settings.retryCap().toSeconds(), 60L << Math.min(10, Math.max(0, attempts)));
        long upperSeconds = Math.min(settings.retryCap().toSeconds(), baseSeconds + Math.max(1, baseSeconds / 2));
        long delay = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(Math.max(1, baseSeconds), Math.max(baseSeconds + 1, upperSeconds + 1));
        return Duration.ofSeconds(delay);
    }

    private static Instant nextBudgetReset(Instant now) {
        return now.atZone(ZoneOffset.UTC)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .plusHours(1)
                .toInstant();
    }

    private static String category(RuntimeException exception) {
        if (exception instanceof DirectUploadException direct) return direct.code();
        return "RECOVERY_PROVIDER_FAILURE";
    }
}
