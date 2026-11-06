package com.ecommerce.media.asset.application.model;

import java.time.Instant;

public record DownloadLink(String url, Instant expiresAt) {}
