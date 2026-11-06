package com.ecommerce.media.asset.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloudinary.Cloudinary;
import com.cloudinary.http5.ApiStrategy;
import com.ecommerce.media.asset.application.exception.StorageFailure;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.asset.application.port.MediaStorage;
import com.ecommerce.media.asset.application.port.MediaStorage.DeleteResult;
import com.ecommerce.media.configuration.MediaProperties;
import com.sun.net.httpserver.HttpServer;

class CloudinaryStorageTest {
    private HttpServer server;
    private final AtomicLong delayMillis = new AtomicLong();
    private volatile int responseStatus = 200;
    private volatile String requestPath;
    private volatile String requestBody;
    private volatile String authorization;
    private volatile String uploadPublicId = "media/item-1";
    private volatile String deleteResult = "ok";
    private CloudinaryStorage storage;
    private MediaProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath = exchange.getRequestURI().toString();
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            try {
                Thread.sleep(delayMillis.get());
                String response;
                if (requestPath.contains("/image/upload")) {
                    response = "{\"asset_id\":\"asset-123\",\"public_id\":\"" + uploadPublicId + "\","
                            + "\"resource_type\":\"image\",\"type\":\"authenticated\",\"format\":\"png\"}";
                } else if (requestPath.contains("/resources/")) {
                    response = "{\"asset_id\":\"asset-123\",\"public_id\":\"media/item-1\","
                            + "\"resource_type\":\"image\",\"type\":\"authenticated\",\"format\":\"png\"}";
                } else {
                    response = "{\"result\":\"" + deleteResult + "\"}";
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        properties = new MediaProperties();
        properties.getStorage().getCloudinary().setCloudName("test-cloud");
        properties.getStorage().getCloudinary().setApiKey("test-key");
        properties.getStorage().getCloudinary().setApiSecret("test-secret");
        properties.getStorage().getCloudinary().setFolder("media");
        properties.getDirectUpload().setEnabled(false);
        properties.getDirectUpload().setProviderCallsPerHour(1);
        properties.setProviderTimeout(Duration.ofSeconds(2));
        Cloudinary cloudinary = new Cloudinary(Map.of(
                "cloud_name", "test-cloud",
                "api_key", "test-key",
                "api_secret", "test-secret",
                "secure", true,
                "upload_prefix", "http://127.0.0.1:" + server.getAddress().getPort()));
        storage = new CloudinaryStorage(properties, cloudinary);
    }

    @AfterEach
    void stopServer() {
        storage.stopProviderExecutor();
        server.stop(0);
    }

    @Test
    void sdkUploadsAuthenticatedImageAtExactKeyWithoutOverwrite() {
        String key = storage.allocateObjectKey(java.util.UUID.fromString("7eac9be4-d532-40bf-b92e-6f864eebafc2"));
        uploadPublicId = key;

        MediaStorage.StoredAsset asset =
                storage.upload(key, new CanonicalImage(new byte[] {1, 2, 3}, "image/png", "png", 1, 1));

        assertThat(key).isEqualTo("media/7eac9be4-d532-40bf-b92e-6f864eebafc2");
        assertThat(requestPath).startsWith("/v1_1/test-cloud/image/upload");
        assertThat(requestBody)
                .contains("name=\"public_id\"", key)
                .contains("name=\"type\"", "authenticated")
                .contains("name=\"overwrite\"", "false", "name=\"api_key\"", "test-key");
        assertThat(asset).isEqualTo(new MediaStorage.StoredAsset("asset-123", key, "image", "authenticated", "png"));
    }

    @Test
    void sdkInspectsAndDeletesOnlyAuthenticatedImageWithProviderIdentity() {
        MediaStorage.StoredAsset inspected = storage.inspect("media/item-1");
        assertThat(requestPath).contains("/v1_1/test-cloud/resources/image/authenticated/media/item-1");
        assertThat(authorization).startsWith("Basic ");
        assertThat(inspected.assetId()).isEqualTo("asset-123");
        assertThat(inspected.publicId()).isEqualTo("media/item-1");
        assertThat(inspected.resourceType()).isEqualTo("image");
        assertThat(inspected.deliveryType()).isEqualTo("authenticated");
        assertThat(inspected.format()).isEqualTo("png");

        DeleteResult deleted = storage.delete("media/item-1");
        assertThat(requestPath).startsWith("/v1_1/test-cloud/image/destroy");
        assertThat(requestBody)
                .contains("name=\"public_id\"", "media/item-1", "name=\"type\"", "authenticated")
                .contains("name=\"invalidate\"", "true");
        assertThat(deleted).isEqualTo(DeleteResult.DELETED);

        deleteResult = "not found";
        assertThat(storage.delete("media/item-1")).isEqualTo(DeleteResult.NOT_FOUND);
    }

    @Test
    void multipartProviderCallsIgnoreTheDirectUploadBudgetWhenDirectUploadIsDisabled() {
        assertThat(properties.getDirectUpload().getProviderCallsPerHour()).isEqualTo(1);
        assertThat(properties.getDirectUpload().isEnabled()).isFalse();

        assertThat(storage.inspect("media/item-1").assetId()).isEqualTo("asset-123");
        assertThat(storage.delete("media/item-1")).isEqualTo(DeleteResult.DELETED);
        assertThat(storage.delete("media/item-1")).isEqualTo(DeleteResult.DELETED);
    }

    @Test
    void rejectsUnexpectedProviderIdentityWithoutLeakingProviderDetails() {
        uploadPublicId = "media/other";

        assertThatThrownBy(() ->
                        storage.upload("media/item-1", new CanonicalImage(new byte[] {1}, "image/png", "png", 1, 1)))
                .isInstanceOf(StorageFailure.class)
                .hasMessage("PROVIDER_IDENTITY_MISMATCH")
                .hasMessageNotContaining("test-secret");
    }

    @Test
    void signedDownloadUsesAuthenticatedTypeAndRequestedExpiry() {
        long expiry = Instant.now().plusSeconds(60).getEpochSecond();

        String link = storage.downloadLink("media/item-1", "png", Instant.ofEpochSecond(expiry));

        assertThat(link)
                .contains("/image/download?")
                .contains("type=authenticated")
                .contains("public_id=media%2Fitem-1")
                .contains("expires_at=" + expiry);
        assertThat(link).doesNotContain("test-secret");
    }

    @Test
    void providerTimeoutAndFailureDetailsStaySanitized() {
        delayMillis.set(2_500);
        MediaProperties properties = new MediaProperties();
        properties.getStorage().getCloudinary().setCloudName("test-cloud");
        properties.getStorage().getCloudinary().setApiKey("test-key");
        properties.getStorage().getCloudinary().setApiSecret("test-secret");
        properties.setProviderTimeout(Duration.ofSeconds(1));
        Cloudinary cloudinary = new Cloudinary(Map.of(
                "cloud_name", "test-cloud",
                "api_key", "test-key",
                "api_secret", "test-secret",
                "upload_prefix", "http://127.0.0.1:" + server.getAddress().getPort()));
        CloudinaryStorage shortTimeout = new CloudinaryStorage(properties, cloudinary);
        try {
            assertThat(cloudinary.config.timeout).isEqualTo(1);
            assertThat(((ApiStrategy) cloudinary.api().getStrategy())
                            .buildRequestConfig()
                            .getResponseTimeout()
                            .toSeconds())
                    .isEqualTo(1);

            long started = System.nanoTime();
            assertThatThrownBy(() -> shortTimeout.inspect("media/item-1"))
                    .isInstanceOf(StorageFailure.class)
                    .hasMessage("PROVIDER_INSPECTION_FAILED")
                    .hasMessageNotContaining("test-secret")
                    .hasMessageNotContaining("127.0.0.1");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(2_000));
        } finally {
            shortTimeout.stopProviderExecutor();
        }
    }
}
