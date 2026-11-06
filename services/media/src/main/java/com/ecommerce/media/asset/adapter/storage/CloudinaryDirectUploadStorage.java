package com.ecommerce.media.asset.adapter.storage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.media.asset.application.exception.DirectUploadException;
import com.ecommerce.media.asset.application.exception.StorageFailure;
import com.ecommerce.media.asset.application.model.DirectUploadPolicy;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;
import com.ecommerce.media.asset.application.port.DirectUploadStorage;
import com.ecommerce.media.configuration.MediaProperties;

@Component
public class CloudinaryDirectUploadStorage implements DirectUploadStorage {
    private final Cloudinary cloudinary;
    private final Cloudinary notificationVerifier;
    private final MediaProperties.DirectUpload properties;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final Semaphore admission;
    private final ThreadPoolExecutor executor;

    @Autowired
    public CloudinaryDirectUploadStorage(MediaProperties properties) {
        this(configuredCloudinary(properties), properties);
    }

    CloudinaryDirectUploadStorage(Cloudinary cloudinary, MediaProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties.getDirectUpload();
        this.cloudName = properties.getStorage().getCloudinary().getCloudName();
        this.apiKey = properties.getStorage().getCloudinary().getApiKey();
        this.apiSecret = properties.getStorage().getCloudinary().getApiSecret();
        String notificationKey = this.properties.getNotificationApiKey();
        this.notificationVerifier = new Cloudinary(ObjectUtils.asMap(
                "cloud_name",
                cloudName,
                "api_key",
                apiKey,
                "api_secret",
                blank(notificationKey) ? apiSecret : notificationKey,
                "secure",
                true));
        int concurrency = Math.max(1, this.properties.getProcessConcurrency());
        admission = new Semaphore(concurrency);
        executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                threadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        cloudinary.config.timeout = Math.toIntExact(
                Math.max(1, this.properties.getInspectionTimeout().toSeconds()));
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static Cloudinary configuredCloudinary(MediaProperties properties) {
        MediaProperties.Cloudinary cloudinary = properties.getStorage().getCloudinary();
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudinary.getCloudName(),
                "api_key", cloudinary.getApiKey(),
                "api_secret", cloudinary.getApiSecret(),
                "secure", true));
    }

    @Override
    public SignedUpload sign(DirectUploadPolicy policy) {
        if (blank(cloudName) || blank(apiKey) || blank(apiSecret)) {
            throw new DirectUploadException(DirectUploadException.Kind.DISABLED);
        }
        Map<String, Object> signed = new LinkedHashMap<>();
        signed.put("timestamp", policy.issuedAt().getEpochSecond());
        signed.put("public_id", policy.publicId());
        signed.put("type", "authenticated");
        signed.put("overwrite", false);
        signed.put("unique_filename", false);
        signed.put("use_filename", false);
        signed.put("allowed_formats", "jpg,png");
        signed.put("transformation", policy.incomingTransformation());
        signed.put("eval", evaluation(policy));
        signed.put("context", "direct_policy=" + policy.policyVersion() + "|direct_intent=" + policy.intentId());
        if (!blank(policy.notificationUrl())) signed.put("notification_url", policy.notificationUrl());
        String signature;
        try {
            signature = cloudinary.apiSignRequest(signed, apiSecret, 1);
        } catch (RuntimeException exception) {
            throw new DirectUploadException(DirectUploadException.Kind.PROVIDER_UNAVAILABLE);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        signed.forEach((key, value) -> fields.put(key, String.valueOf(value)));
        fields.put("api_key", apiKey);
        fields.put("signature", signature);
        return new SignedUpload("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload", Map.copyOf(fields));
    }

    static String evaluation(DirectUploadPolicy policy) {
        return "var i=resource_info;"
                + "function n(v){return typeof v==='number'&&isFinite(v)&&Math.floor(v)===v&&v>0;}"
                + "function one(v){return v===undefined||n(v)&&v===1;}"
                + "if(!i||!n(i.bytes)||!n(i.width)||!n(i.height)||typeof i.format!=='string')"
                + "throw new Error('INPUT_METADATA_UNAVAILABLE');"
                + "var f=i.format.toLowerCase();"
                + "if(i.bytes>" + policy.maxBytes() + "||i.width>" + policy.maxSide() + "||i.height>" + policy.maxSide()
                + "||i.width*i.height>" + policy.maxPixels() + "||['jpg','jpeg','png'].indexOf(f)<0)"
                + "throw new Error('INPUT_POLICY_REJECTED');"
                + "if(!one(i.pages)||!one(i.frames)||(i.is_animated!==undefined&&i.is_animated!==false))"
                + "throw new Error('UNSUPPORTED_INPUT');"
                + "upload_options.context='direct_policy=" + policy.policyVersion() + "|direct_intent="
                + policy.intentId()
                + "|source_bytes='+i.bytes+'|source_width='+i.width+'|source_height='+i.height+'|source_format='+f;";
    }

    @Override
    public TrustedProviderInspection inspect(String publicId, Duration timeout) {
        return providerCall(
                () -> {
                    Map<?, ?> response = cloudinary
                            .api()
                            .resource(
                                    publicId,
                                    ObjectUtils.asMap(
                                            "resource_type", "image", "type", "authenticated", "context", true));
                    return inspection(response);
                },
                timeout);
    }

    @Override
    public DiscoveryPage discover(String publicIdPrefix, String resourceType, String nextCursor, int pageSize) {
        return providerCall(() -> {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("resource_type", resourceType);
            params.put("type", "authenticated");
            params.put("prefix", publicIdPrefix);
            params.put("max_results", pageSize);
            if (nextCursor != null && !nextCursor.isBlank()) params.put("next_cursor", nextCursor);
            ApiResponse response = cloudinary.api().resources(params);
            Object resources = response.get("resources");
            if (!(resources instanceof List<?> list)) return new DiscoveryPage(List.of(), null);
            List<DiscoveredObject> found = new ArrayList<>();
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> item)) continue;
                String publicId = string(item.get("public_id"));
                String assetId = string(item.get("asset_id"));
                String type = string(item.get("resource_type"));
                String deliveryType = string(item.get("type"));
                if (!blank(publicId) && !blank(assetId) && resourceType.equals(type)) {
                    found.add(new DiscoveredObject(type, deliveryType, publicId, assetId));
                }
            }
            return new DiscoveryPage(List.copyOf(found), string(response.get("next_cursor")));
        });
    }

    @Override
    public void delete(DiscoveredObject object) {
        if (!"authenticated".equals(object.deliveryType())
                || !List.of("image", "raw", "video").contains(object.resourceType())) {
            throw new DirectUploadException(DirectUploadException.Kind.BAD_REQUEST);
        }
        providerCall(() -> {
            Map<?, ?> result = cloudinary
                    .uploader()
                    .destroy(
                            object.publicId(),
                            ObjectUtils.asMap(
                                    "resource_type",
                                    object.resourceType(),
                                    "type",
                                    "authenticated",
                                    "invalidate",
                                    true));
            Object status = result.get("result");
            if (!("ok".equals(status) || "not found".equals(status))) {
                throw new StorageFailure(StorageFailure.Category.DELETE_OUTCOME_UNKNOWN);
            }
            return null;
        });
    }

    @Override
    public boolean verifyNotification(String body, String timestamp, String signature, long toleranceSeconds) {
        String key = properties.getNotificationApiKey();
        if (blank(key)) return false;
        return notificationVerifier.verifyNotificationSignature(body, timestamp, signature, toleranceSeconds);
    }

    private TrustedProviderInspection inspection(Map<?, ?> response) {
        Map<?, ?> context = map(response.get("context"));
        Map<?, ?> custom = map(context.get("custom"));
        String evidence = string(custom.get("direct_policy"));
        String intent = string(custom.get("direct_intent"));
        UUID intentId = null;
        try {
            if (intent != null) intentId = UUID.fromString(intent);
        } catch (IllegalArgumentException ignored) {
            // Invalid provider context is returned as untrusted evidence and rejected by the application service.
        }
        Long sourceBytes = longValue(custom.get("source_bytes"));
        Integer sourceWidth = intValue(custom.get("source_width"));
        Integer sourceHeight = intValue(custom.get("source_height"));
        return new TrustedProviderInspection(
                string(response.get("asset_id")),
                string(response.get("public_id")),
                string(response.get("resource_type")),
                string(response.get("type")),
                string(response.get("format")),
                longValue(response.get("bytes")),
                intValue(response.get("width")),
                intValue(response.get("height")),
                evidence,
                intentId,
                sourceBytes,
                sourceWidth,
                sourceHeight,
                string(custom.get("source_format")),
                custom.toString());
    }

    private <T> T providerCall(java.util.concurrent.Callable<T> call) {
        return providerCall(call, properties.getInspectionTimeout());
    }

    private <T> T providerCall(java.util.concurrent.Callable<T> call, Duration timeout) {
        if (!admission.tryAcquire()) throw new DirectUploadException(DirectUploadException.Kind.QUOTA_EXCEEDED, 1);
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(call);
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    admission.release();
                }
            });
        } catch (RuntimeException exception) {
            admission.release();
            throw new DirectUploadException(DirectUploadException.Kind.QUOTA_EXCEEDED, 1);
        }
        try {
            return task.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new DirectUploadException(DirectUploadException.Kind.PROVIDER_UNAVAILABLE);
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new DirectUploadException(DirectUploadException.Kind.PROVIDER_UNAVAILABLE);
        } catch (ExecutionException exception) {
            throw new DirectUploadException(DirectUploadException.Kind.PROVIDER_UNAVAILABLE);
        }
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return Double.isFinite(number.doubleValue()) && number.doubleValue() == parsed ? parsed : null;
        }
        if (value instanceof String text && text.matches("[0-9]+")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null || parsed > Integer.MAX_VALUE ? null : parsed.intValue();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ThreadFactory threadFactory() {
        return task -> {
            Thread thread = new Thread(task, "media-direct-provider");
            thread.setDaemon(true);
            return thread;
        };
    }
}
