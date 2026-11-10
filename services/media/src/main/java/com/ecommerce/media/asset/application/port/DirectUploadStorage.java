package com.ecommerce.media.asset.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.ecommerce.media.asset.application.model.DirectUploadPolicy;
import com.ecommerce.media.asset.application.model.TrustedProviderInspection;

public interface DirectUploadStorage {
    record SignedUpload(String uploadUrl, Map<String, String> fields) {}

    record DiscoveredObject(String resourceType, String deliveryType, String publicId, String assetId) {}

    record DiscoveryPage(List<DiscoveredObject> objects, String nextCursor) {}

    SignedUpload sign(DirectUploadPolicy policy);

    TrustedProviderInspection inspect(String publicId, Duration timeout);

    DiscoveryPage discover(String publicIdPrefix, String resourceType, String nextCursor, int pageSize);

    void delete(DiscoveredObject object);

    boolean verifyNotification(String body, String timestamp, String signature, long toleranceSeconds);
}
