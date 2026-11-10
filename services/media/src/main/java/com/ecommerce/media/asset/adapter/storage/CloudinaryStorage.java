package com.ecommerce.media.asset.adapter.storage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.media.asset.application.exception.StorageFailure;
import com.ecommerce.media.asset.application.exception.StorageFailure.Category;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.asset.application.port.MediaStorage;
import com.ecommerce.media.config.MediaProperties;

@Component
public class CloudinaryStorage implements MediaStorage {
    private final Cloudinary cloudinary;
    private final String folder;
    private final long timeoutMillis;
    private final ExecutorService providerExecutor = new ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16),
            providerThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public CloudinaryStorage(MediaProperties properties) {
        this(properties, configuredCloudinary(properties));
    }

    CloudinaryStorage(MediaProperties properties, Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
        MediaProperties.Cloudinary cloudinaryProperties =
                properties.getStorage().getCloudinary();
        this.folder = cloudinaryProperties.getFolder().replaceAll("^/+|/+$", "");
        timeoutMillis = properties.getProviderTimeout().toMillis();
        this.cloudinary.config.timeout = Math.toIntExact(Math.max(1, (timeoutMillis + 999) / 1000));
    }

    @PreDestroy
    void stopProviderExecutor() {
        providerExecutor.shutdownNow();
    }

    private static Cloudinary configuredCloudinary(MediaProperties properties) {
        MediaProperties.Cloudinary cloudinaryProperties =
                properties.getStorage().getCloudinary();
        if (blank(cloudinaryProperties.getCloudName())
                || blank(cloudinaryProperties.getApiKey())
                || blank(cloudinaryProperties.getApiSecret())) {
            throw new IllegalStateException(
                    "CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET are required.");
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudinaryProperties.getCloudName(),
                "api_key", cloudinaryProperties.getApiKey(),
                "api_secret", cloudinaryProperties.getApiSecret(),
                "secure", true));
    }

    @Override
    public String allocateObjectKey(UUID mediaId) {
        return folder.isBlank() ? mediaId.toString() : folder + "/" + mediaId;
    }

    @Override
    public StoredAsset inspect(String publicId) {
        try {
            Map<?, ?> response = providerCall(
                    Category.PROVIDER_INSPECTION_FAILED,
                    () -> cloudinary
                            .api()
                            .resource(publicId, ObjectUtils.asMap("resource_type", "image", "type", "authenticated")));
            return new StoredAsset(
                    String.valueOf(response.get("asset_id")),
                    String.valueOf(response.get("public_id")),
                    String.valueOf(response.get("resource_type")),
                    String.valueOf(response.get("type")),
                    String.valueOf(response.get("format")));
        } catch (Exception exception) {
            throw new StorageFailure(Category.PROVIDER_INSPECTION_FAILED);
        }
    }

    @Override
    public StoredAsset upload(String publicId, CanonicalImage image) {
        try {
            Map<?, ?> response = providerCall(
                    Category.UPLOAD_OUTCOME_UNKNOWN,
                    () -> cloudinary
                            .uploader()
                            .upload(
                                    image.bytes(),
                                    ObjectUtils.asMap(
                                            "public_id",
                                            publicId,
                                            "resource_type",
                                            "image",
                                            "type",
                                            "authenticated",
                                            "overwrite",
                                            false,
                                            "unique_filename",
                                            false,
                                            "use_filename",
                                            false,
                                            "format",
                                            image.format())));
            String returnedId = String.valueOf(response.get("public_id"));
            String assetId = String.valueOf(response.get("asset_id"));
            String resourceType = String.valueOf(response.get("resource_type"));
            String deliveryType = String.valueOf(response.get("type"));
            String format = String.valueOf(response.get("format"));
            if (!publicId.equals(returnedId)
                    || assetId.equals("null")
                    || !resourceType.equals("image")
                    || !deliveryType.equals("authenticated")
                    || !format.equals(image.format())) {
                throw new StorageFailure(Category.PROVIDER_IDENTITY_MISMATCH);
            }
            return new StoredAsset(assetId, returnedId, resourceType, deliveryType, format);
        } catch (StorageFailure exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StorageFailure(Category.UPLOAD_OUTCOME_UNKNOWN);
        }
    }

    @Override
    public String downloadLink(String publicId, String format, Instant expiresAt) {
        try {
            long expiry = expiresAt.getEpochSecond();
            return cloudinary.privateDownload(
                    publicId,
                    format,
                    Map.<String, Object>of(
                            "resource_type", "image", "type", "authenticated", "expires_at", expiry, "secure", true));
        } catch (Exception exception) {
            throw new StorageFailure(Category.DOWNLOAD_LINK_GENERATION_FAILED);
        }
    }

    @Override
    public DeleteResult delete(String publicId) {
        try {
            Map<?, ?> response = providerCall(
                    Category.DELETE_OUTCOME_UNKNOWN,
                    () -> cloudinary
                            .uploader()
                            .destroy(
                                    publicId,
                                    ObjectUtils.asMap(
                                            "resource_type", "image", "type", "authenticated", "invalidate", true)));
            Object result = response.get("result");
            if ("ok".equals(result) || "deleted".equals(result)) {
                return DeleteResult.DELETED;
            }
            if ("not found".equals(result)) {
                return DeleteResult.NOT_FOUND;
            }
            throw new StorageFailure(Category.DELETE_OUTCOME_UNKNOWN);
        } catch (StorageFailure exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StorageFailure(Category.DELETE_OUTCOME_UNKNOWN);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T providerCall(Category category, Callable<T> action) {
        Future<T> result;
        try {
            result = providerExecutor.submit(action);
        } catch (RuntimeException exception) {
            throw new StorageFailure(category);
        }
        try {
            return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            result.cancel(true);
            throw new StorageFailure(category);
        } catch (InterruptedException exception) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw new StorageFailure(category);
        } catch (ExecutionException exception) {
            throw new StorageFailure(category);
        }
    }

    private static ThreadFactory providerThreadFactory() {
        return task -> {
            Thread thread = new Thread(task, "media-cloudinary-provider");
            thread.setDaemon(true);
            return thread;
        };
    }
}
