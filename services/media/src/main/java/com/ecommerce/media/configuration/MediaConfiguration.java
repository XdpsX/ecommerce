package com.ecommerce.media.configuration;

import java.time.Clock;
import java.util.concurrent.Semaphore;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
@EnableScheduling
public class MediaConfiguration {
    @Bean
    Clock mediaClock() {
        return Clock.systemUTC();
    }

    @Bean
    Semaphore imageDecodePermits(MediaProperties properties) {
        if (properties.getMaxConcurrentDecodes() < 1) {
            throw new IllegalStateException("media.max-concurrent-decodes must be at least 1.");
        }
        return new Semaphore(properties.getMaxConcurrentDecodes());
    }

    @Bean
    org.springframework.boot.ApplicationRunner validateMediaOperationalSettings(MediaProperties properties) {
        return args -> {
            if (properties.getDownloadLinkTtl().isZero()
                    || properties.getDownloadLinkTtl().isNegative()
                    || properties.getDownloadLinkTtl().compareTo(java.time.Duration.ofHours(1)) > 0
                    || properties.getProviderTimeout().isZero()
                    || properties.getProviderTimeout().isNegative()
                    || properties.getMaxInputBytes() < 1
                    || properties.getMaxOutputBytes() < 1
                    || properties.getMaxPixels() < 1
                    || properties.getMaxSide() < 1
                    || properties.getCleanup().getBatchSize() < 1
                    || properties.getCleanup().getLeaseDuration().isZero()
                    || properties.getCleanup().getLeaseDuration().compareTo(properties.getProviderTimeout()) <= 0
                    || properties.getCleanup().getAbandonAfter().compareTo(properties.getProviderTimeout()) <= 0) {
                throw new IllegalStateException(
                        "Media size, TTL and cleanup settings must be positive and within safe bounds.");
            }
        };
    }
}
