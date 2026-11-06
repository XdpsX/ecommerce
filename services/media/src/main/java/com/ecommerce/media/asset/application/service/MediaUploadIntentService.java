package com.ecommerce.media.asset.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ecommerce.media.asset.application.exception.DirectUploadException;
import com.ecommerce.media.asset.application.exception.MediaPersistenceLockTimeoutException;
import com.ecommerce.media.asset.application.model.DirectUploadBatch;
import com.ecommerce.media.asset.application.model.DirectUploadInstruction;
import com.ecommerce.media.asset.application.model.DirectUploadPolicy;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.model.UploadIntentBatchResult;
import com.ecommerce.media.asset.application.model.UploadIntentDeclaration;
import com.ecommerce.media.asset.application.model.UploadIntentOutcome;
import com.ecommerce.media.asset.application.model.UploadIntentOutcome.ErrorCode;
import com.ecommerce.media.asset.application.model.UploadIntentOutcome.Status;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.asset.application.port.MediaAssetRepository;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository;
import com.ecommerce.media.asset.application.port.MediaUploadIntentRepository.QuotaLimits;
import com.ecommerce.media.asset.domain.AssetStatus;
import com.ecommerce.media.asset.domain.MediaAsset;
import com.ecommerce.media.asset.domain.MediaUploadIntent;
import com.ecommerce.media.asset.domain.UploadIntentState;
import com.ecommerce.media.configuration.MediaProperties;

@Service
public class MediaUploadIntentService {
    static final String POLICY_VERSION = "direct-a1-v1";
    private static final long MAX_POLICY_INPUT_BYTES = 5_242_880L;
    private static final long MAX_POLICY_OUTPUT_BYTES = 4_194_304L;
    private static final int MAX_POLICY_SIDE = 10_000;
    private static final long MAX_POLICY_PIXELS = 20_000_000L;
    private final MediaUploadIntentRepository repository;
    private final MediaAssetRepository assets;
    private final DirectUploadStorage storage;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public MediaUploadIntentService(
            MediaUploadIntentRepository repository,
            MediaAssetRepository assets,
            DirectUploadStorage storage,
            MediaProperties mediaProperties,
            Clock mediaClock) {
        this.repository = repository;
        this.assets = assets;
        this.storage = storage;
        this.mediaProperties = mediaProperties;
        this.clock = mediaClock;
    }

    public DirectUploadBatch createBatch(UUID ownerId, List<UploadIntentDeclaration> declarations) {
        MediaProperties.DirectUpload settings = settings();
        requireFeatureEnabled(settings);
        requireIssuanceEnabled(settings);
        validateDeclarations(declarations, settings);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.getIntentTtl());
        String root =
                normalizedFolder(mediaProperties.getStorage().getCloudinary().getFolder());
        if (root.length() + 1 + "direct/".length() + 36 > 255
                || root.contains("//")
                || List.of(root.split("/")).contains("..")) {
            throw new DirectUploadException(DirectUploadException.Kind.DISABLED);
        }
        List<MediaUploadIntent> intents = new ArrayList<>(declarations.size());
        for (UploadIntentDeclaration declaration : declarations) {
            UUID id = UUID.randomUUID();
            String publicId = (root.isBlank() ? "" : root + "/") + "direct/" + id;
            intents.add(new MediaUploadIntent(
                    id,
                    ownerId,
                    declaration.clientFileId(),
                    declaration.filename(),
                    declaration.contentType(),
                    declaration.sizeBytes(),
                    POLICY_VERSION,
                    publicId,
                    UploadIntentState.PENDING,
                    now,
                    now,
                    expiresAt,
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
                    now.plus(min(
                            settings.getReconcileInterval(),
                            settings.getIntentTtl().dividedBy(2))),
                    now.plus(Duration.ofHours(1)),
                    null,
                    "image",
                    null,
                    null,
                    0));
        }
        repository.createBatch(
                intents,
                new QuotaLimits(
                        settings.getPerPrincipalDailyIntents(),
                        settings.getGlobalDailyIntents(),
                        settings.getPerPrincipalActiveIntents(),
                        settings.getGlobalActiveIntents(),
                        settings.getIssuanceStopBacklog(),
                        settings.getIssuanceStopOldestRecovery()),
                now);

        String callback = settings.isWebhookEnabled() ? settings.getNotificationUrl() : "";
        List<DirectUploadInstruction> instructions = new ArrayList<>(intents.size());
        try {
            for (int index = 0; index < intents.size(); index++) {
                MediaUploadIntent intent = intents.get(index);
                DirectUploadPolicy policy = new DirectUploadPolicy(
                        intent.id(),
                        intent.providerPublicId(),
                        intent.policyVersion(),
                        Math.min(
                                intent.declaredSizeBytes(),
                                Math.min(settings.getMaxFileBytes(), MAX_POLICY_INPUT_BYTES)),
                        effectiveMaxSide(),
                        effectiveMaxPixels(),
                        intent.signedAt(),
                        intent.expiresAt(),
                        "a_auto/fl_strip_profile",
                        callback);
                DirectUploadStorage.SignedUpload signed = storage.sign(policy);
                instructions.add(new DirectUploadInstruction(
                        declarations.get(index).clientFileId(),
                        intent.id(),
                        signed.uploadUrl(),
                        signed.fields(),
                        intent.expiresAt()));
            }
        } catch (RuntimeException exception) {
            throw new DirectUploadException(DirectUploadException.Kind.PROVIDER_UNAVAILABLE);
        }
        return new DirectUploadBatch(List.copyOf(instructions));
    }

    public UploadIntentBatchResult completeBatch(UUID ownerId, List<UUID> intentIds) {
        validateIds(intentIds);
        MediaProperties.DirectUpload settings = settings();
        requireFeatureEnabled(settings);
        long deadlineNanos = System.nanoTime() + settings.getBatchDeadline().toNanos();
        List<UploadIntentOutcome> outcomes = new ArrayList<>(intentIds.size());
        for (UUID id : intentIds) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= Duration.ofSeconds(1).toNanos()) {
                outcomes.add(outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE));
                continue;
            }
            outcomes.add(completeOne(ownerId, id, settings, deadlineNanos));
        }
        return new UploadIntentBatchResult(List.copyOf(outcomes));
    }

    public UploadIntentBatchResult getStatuses(UUID ownerId, List<UUID> intentIds) {
        validateIds(intentIds);
        requireFeatureEnabled(settings());
        List<UploadIntentOutcome> outcomes = intentIds.stream()
                .map(id -> repository
                        .findOwned(ownerId, id)
                        .map(this::currentOutcome)
                        .orElseGet(() -> outcome(id, Status.NOT_FOUND, null, null)))
                .toList();
        return new UploadIntentBatchResult(outcomes);
    }

    public boolean scheduleInspection(String providerPublicId) {
        return repository.scheduleInspection(providerPublicId, clock.instant());
    }

    private UploadIntentOutcome completeOne(
            UUID ownerId, UUID id, MediaProperties.DirectUpload settings, long deadlineNanos) {
        var existing = repository.findOwned(ownerId, id);
        if (existing.isEmpty()) return outcome(id, Status.NOT_FOUND, null, null);
        MediaUploadIntent intent = existing.get();
        if (intent.state() != UploadIntentState.PENDING) return currentOutcome(intent);
        Instant now = clock.instant();
        if (!intent.expiresAt().isAfter(now)) return outcome(id, Status.EXPIRED, null, null);
        Duration maxLockWait = inspectionBudgetRemaining(deadlineNanos);
        if (maxLockWait.isZero()) return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        UUID lease = UUID.randomUUID();
        boolean claimed;
        try {
            claimed = repository.claimForCompletion(
                    id, ownerId, lease, now, now.plus(settings.getLeaseDuration()), maxLockWait);
        } catch (MediaPersistenceLockTimeoutException exception) {
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        }
        if (!claimed) {
            return repository
                    .findOwned(ownerId, id)
                    .map(this::currentOutcome)
                    .orElseGet(() -> outcome(id, Status.NOT_FOUND, null, null));
        }
        if (inspectionBudgetRemaining(deadlineNanos).isZero()) {
            retryWithinDeadline(id, lease, deadlineNanos, "BATCH_DEADLINE", retryAt(intent, clock.instant(), settings));
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        }
        return inspectAndFinalize(intent, lease, settings, deadlineNanos, false);
    }

    UploadIntentOutcome reconcilePending(
            MediaUploadIntent intent, UUID lease, MediaProperties.DirectUpload settings, Instant now) {
        if (!intent.expiresAt().isAfter(now)) {
            repository.expire(intent.id(), lease, now, now);
            return outcome(intent.id(), Status.EXPIRED, null, null);
        }
        long deadlineNanos = System.nanoTime() + settings.getBatchDeadline().toNanos();
        return inspectAndFinalize(intent, lease, settings, deadlineNanos, true);
    }

    private UploadIntentOutcome inspectAndFinalize(
            MediaUploadIntent intent,
            UUID lease,
            MediaProperties.DirectUpload settings,
            long deadlineNanos,
            boolean recovery) {
        UUID id = intent.id();
        if (inspectionBudgetRemaining(deadlineNanos).isZero()) {
            retryWithinDeadline(id, lease, deadlineNanos, "BATCH_DEADLINE", retryAt(intent, clock.instant(), settings));
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        }
        try {
            Instant now = clock.instant();
            Instant hour = now.atZone(ZoneOffset.UTC)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .toInstant();
            Duration budgetLockWait = inspectionBudgetRemaining(deadlineNanos);
            if (budgetLockWait.isZero()) {
                retryWithinDeadline(
                        id, lease, deadlineNanos, "BATCH_DEADLINE", retryAt(intent, clock.instant(), settings));
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            if (!repository.reserveProviderCall(
                    hour,
                    recovery,
                    settings.getProviderCallsPerHour(),
                    settings.getReservedRecoveryCallsPerHour(),
                    budgetLockWait)) {
                retryWithinDeadline(
                        id, lease, deadlineNanos, "PROVIDER_BUDGET_EXHAUSTED", hour.plus(Duration.ofHours(1)));
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }

            Duration inspectionTimeout = min(settings.getInspectionTimeout(), inspectionBudgetRemaining(deadlineNanos));
            if (inspectionTimeout.isZero() || inspectionTimeout.isNegative()) {
                retryWithinDeadline(
                        id, lease, deadlineNanos, "BATCH_DEADLINE", retryAt(intent, clock.instant(), settings));
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            TrustedProviderInspection inspection = storage.inspect(intent.providerPublicId(), inspectionTimeout);
            ErrorCode rejection = validateInspection(intent, inspection, settings);
            if (rejection != null) {
                boolean rejected = rejectWithinDeadline(id, lease, deadlineNanos, rejection.name());
                return rejected
                        ? outcome(id, Status.REJECTED, null, rejection)
                        : outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            UUID mediaId = UUID.randomUUID();
            String mediaType = inspection.format().equalsIgnoreCase("jpg")
                            || inspection.format().equalsIgnoreCase("jpeg")
                    ? "image/jpeg"
                    : "image/png";
            MediaAsset ready = new MediaAsset(
                    mediaId,
                    intent.ownerId(),
                    AssetStatus.READY,
                    mediaType,
                    inspection.finalBytes(),
                    inspection.finalWidth(),
                    inspection.finalHeight(),
                    intent.providerPublicId(),
                    inspection.assetId(),
                    clock.instant(),
                    clock.instant(),
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    1);
            Duration finalizationLockWait = inspectionBudgetRemaining(deadlineNanos);
            if (finalizationLockWait.isZero()) {
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            boolean finalized;
            try {
                finalized = repository.completeAndCreateAsset(
                        id, lease, ready, inspection, clock.instant(), finalizationLockWait);
            } catch (MediaPersistenceLockTimeoutException exception) {
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            if (!finalized) {
                retryWithinDeadline(
                        id, lease, deadlineNanos, "FINALIZATION_RACE", retryAt(intent, clock.instant(), settings));
                return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
            }
            return outcome(id, Status.READY, mediaId, null);
        } catch (MediaPersistenceLockTimeoutException exception) {
            retryWithinDeadline(
                    id, lease, deadlineNanos, "DATABASE_LOCK_TIMEOUT", retryAt(intent, clock.instant(), settings));
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        } catch (DirectUploadException exception) {
            retryWithinDeadline(id, lease, deadlineNanos, exception.code(), retryAt(intent, clock.instant(), settings));
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        } catch (RuntimeException exception) {
            retryWithinDeadline(
                    id, lease, deadlineNanos, "PROVIDER_INSPECTION_FAILED", retryAt(intent, clock.instant(), settings));
            return outcome(id, Status.PENDING, null, ErrorCode.RETRYABLE);
        }
    }

    private boolean retryWithinDeadline(
            UUID id, UUID lease, long deadlineNanos, String category, Instant nextAttemptAt) {
        Duration maxLockWait = inspectionBudgetRemaining(deadlineNanos);
        if (maxLockWait.isZero()) return false;
        try {
            return repository.retry(id, lease, category, nextAttemptAt, clock.instant(), maxLockWait);
        } catch (MediaPersistenceLockTimeoutException exception) {
            return false;
        }
    }

    private boolean rejectWithinDeadline(UUID id, UUID lease, long deadlineNanos, String errorCode) {
        Duration maxLockWait = inspectionBudgetRemaining(deadlineNanos);
        if (maxLockWait.isZero()) return false;
        Instant now = clock.instant();
        try {
            return repository.reject(id, lease, errorCode, now, now, maxLockWait);
        } catch (MediaPersistenceLockTimeoutException exception) {
            return false;
        }
    }

    ErrorCode validateInspection(
            MediaUploadIntent intent, TrustedProviderInspection inspection, MediaProperties.DirectUpload settings) {
        if (inspection == null
                || !intent.providerPublicId().equals(inspection.publicId())
                || inspection.assetId() == null
                || !"image".equals(inspection.resourceType())
                || !"authenticated".equals(inspection.deliveryType())) {
            return ErrorCode.PROVIDER_IDENTITY_MISMATCH;
        }
        if (!POLICY_VERSION.equals(inspection.policyVersion())
                || !intent.id().equals(inspection.intentId())
                || inspection.sourceBytes() == null
                || inspection.sourceBytes() < 1
                || inspection.sourceBytes()
                        > Math.min(
                                intent.declaredSizeBytes(),
                                Math.min(settings.getMaxFileBytes(), MAX_POLICY_INPUT_BYTES))
                || inspection.sourceWidth() == null
                || inspection.sourceHeight() == null
                || inspection.sourceWidth() < 1
                || inspection.sourceHeight() < 1
                || inspection.sourceWidth() > effectiveMaxSide()
                || inspection.sourceHeight() > effectiveMaxSide()
                || (long) inspection.sourceWidth() * inspection.sourceHeight() > effectiveMaxPixels()
                || inspection.sourceFormat() == null
                || !Set.of("jpg", "jpeg", "png")
                        .contains(inspection.sourceFormat().toLowerCase(Locale.ROOT))) {
            return ErrorCode.INPUT_POLICY_REJECTED;
        }
        String expectedSourceType = "image/jpeg".equals(intent.declaredContentType()) ? "jpeg" : "png";
        if (!(inspection.sourceFormat().equalsIgnoreCase(expectedSourceType)
                || expectedSourceType.equals("jpeg")
                        && inspection.sourceFormat().equalsIgnoreCase("jpg"))) {
            return ErrorCode.INPUT_POLICY_REJECTED;
        }
        if (inspection.finalBytes() == null
                || inspection.finalBytes() < 1
                || inspection.finalBytes() > Math.min(mediaProperties.getMaxOutputBytes(), MAX_POLICY_OUTPUT_BYTES)) {
            return ErrorCode.OUTPUT_LIMIT_EXCEEDED;
        }
        if (inspection.finalWidth() == null
                || inspection.finalHeight() == null
                || inspection.finalWidth() < 1
                || inspection.finalHeight() < 1
                || inspection.finalWidth() > effectiveMaxSide()
                || inspection.finalHeight() > effectiveMaxSide()
                || (long) inspection.finalWidth() * inspection.finalHeight() > effectiveMaxPixels()
                || inspection.format() == null
                || !Set.of("jpg", "jpeg", "png").contains(inspection.format().toLowerCase(Locale.ROOT))) {
            return ErrorCode.INPUT_POLICY_REJECTED;
        }
        return null;
    }

    private UploadIntentOutcome currentOutcome(MediaUploadIntent intent) {
        if (intent.state() == UploadIntentState.REJECTED) {
            return outcome(intent.id(), Status.REJECTED, null, errorCode(intent.errorCode()));
        }
        if (intent.state() == UploadIntentState.EXPIRED) return outcome(intent.id(), Status.EXPIRED, null, null);
        if (intent.state() == UploadIntentState.PENDING && !intent.expiresAt().isAfter(clock.instant())) {
            return outcome(intent.id(), Status.EXPIRED, null, null);
        }
        if (intent.state() == UploadIntentState.PENDING) {
            return outcome(intent.id(), Status.PENDING, null, ErrorCode.RETRYABLE);
        }
        if (intent.mediaId() == null) return outcome(intent.id(), Status.NOT_FOUND, null, null);
        return assets.find(intent.mediaId())
                .map(asset -> switch (asset.status()) {
                    case READY -> outcome(intent.id(), Status.READY, asset.id(), null);
                    case DELETED -> outcome(intent.id(), Status.DELETED, null, null);
                    default -> outcome(intent.id(), Status.UNAVAILABLE, null, null);
                })
                .orElseGet(() -> outcome(intent.id(), Status.NOT_FOUND, null, null));
    }

    private void validateDeclarations(
            List<UploadIntentDeclaration> declarations, MediaProperties.DirectUpload settings) {
        if (declarations == null || declarations.isEmpty() || declarations.size() > settings.getBatchSize()) {
            throw new DirectUploadException(DirectUploadException.Kind.BAD_REQUEST);
        }
        Set<String> fileIds = new HashSet<>();
        long total = 0;
        for (UploadIntentDeclaration declaration : declarations) {
            if (declaration == null
                    || declaration.clientFileId() == null
                    || declaration.clientFileId().isBlank()
                    || declaration.clientFileId().length() > 64
                    || !fileIds.add(declaration.clientFileId())
                    || declaration.filename() == null
                    || declaration.filename().isBlank()
                    || declaration.filename().length() > 255
                    || declaration.filename().chars().anyMatch(ch -> ch == 0 || ch == '/' || ch == '\\')
                    || declaration.contentType() == null
                    || !Set.of("image/jpeg", "image/png").contains(declaration.contentType())
                    || declaration.sizeBytes() < 1
                    || declaration.sizeBytes()
                            > Math.min(
                                    Math.min(settings.getMaxFileBytes(), MAX_POLICY_INPUT_BYTES),
                                    mediaProperties.getMaxInputBytes())) {
                throw new DirectUploadException(DirectUploadException.Kind.BAD_REQUEST);
            }
            total = Math.addExact(total, declaration.sizeBytes());
            if (total > settings.getMaxBatchBytes())
                throw new DirectUploadException(DirectUploadException.Kind.BAD_REQUEST);
        }
    }

    private void validateIds(List<UUID> ids) {
        if (ids == null
                || ids.isEmpty()
                || ids.size() > settings().getBatchSize()
                || ids.stream().anyMatch(id -> id == null)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new DirectUploadException(DirectUploadException.Kind.BAD_REQUEST);
        }
    }

    private void requireFeatureEnabled(MediaProperties.DirectUpload settings) {
        if (!settings.isEnabled()) throw new DirectUploadException(DirectUploadException.Kind.DISABLED);
    }

    private void requireIssuanceEnabled(MediaProperties.DirectUpload settings) {
        if (!settings.isIssuanceEnabled() || !repository.issuanceEnabled()) {
            throw DirectUploadException.retry(Duration.ofMinutes(1));
        }
        if (settings.isWebhookEnabled()
                && (!settings.getNotificationUrl().startsWith("https://")
                        || settings.getNotificationApiKey().isBlank())) {
            throw new DirectUploadException(DirectUploadException.Kind.DISABLED);
        }
    }

    private MediaProperties.DirectUpload settings() {
        return mediaProperties.getDirectUpload();
    }

    private int effectiveMaxSide() {
        return Math.min(mediaProperties.getMaxSide(), MAX_POLICY_SIDE);
    }

    private long effectiveMaxPixels() {
        return Math.min(mediaProperties.getMaxPixels(), MAX_POLICY_PIXELS);
    }

    private static String normalizedFolder(String folder) {
        return folder == null ? "" : folder.replaceAll("^/+|/+$", "");
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static Duration inspectionBudgetRemaining(long deadlineNanos) {
        long remaining =
                deadlineNanos - System.nanoTime() - Duration.ofSeconds(1).toNanos();
        return remaining <= 0 ? Duration.ZERO : Duration.ofNanos(remaining);
    }

    private static Instant retryAt(MediaUploadIntent intent, Instant now, MediaProperties.DirectUpload settings) {
        long baseSeconds = Math.min(settings.getRetryCap().toSeconds(), 60L << Math.min(10, intent.retryCount()));
        long upperSeconds = Math.min(settings.getRetryCap().toSeconds(), baseSeconds + Math.max(1, baseSeconds / 2));
        long delay = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(Math.max(1, baseSeconds), Math.max(baseSeconds + 1, upperSeconds + 1));
        return now.plusSeconds(delay);
    }

    private static ErrorCode errorCode(String value) {
        try {
            return value == null ? null : ErrorCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return ErrorCode.UNSUPPORTED_INPUT;
        }
    }

    private static UploadIntentOutcome outcome(UUID id, Status status, UUID mediaId, ErrorCode errorCode) {
        return new UploadIntentOutcome(id, status, mediaId, errorCode);
    }
}
