package com.ecommerce.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    @Test
    void keepsAssetLayersIndependentFromTheirAdapters() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/ecommerce/media");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path :
                    paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                boolean composition = relative.startsWith("config/");
                if (relative.startsWith("asset/domain/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain("org.springframework", ".application.", ".adapter.", ".shared.");
                }
                if (relative.startsWith("asset/application/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    ".adapter.",
                                    "com.ecommerce.media.shared.",
                                    "com.ecommerce.media.config.",
                                    "org.springframework.http",
                                    "org.springframework.jdbc",
                                    "com.cloudinary");
                }
                if (relative.startsWith("asset/adapter/web/")) {
                    assertThat(source).as(relative).doesNotContain("org.springframework.jdbc", ".adapter.persistence.");
                }
                if (relative.startsWith("shared/")) {
                    assertThat(relative)
                            .as("shared HTTP placement: %s", relative)
                            .startsWith("shared/error/");
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "org.springframework.jdbc",
                                    "jakarta.persistence",
                                    ".asset.adapter.",
                                    ".asset.application.service.",
                                    ".asset.application.port.",
                                    ".config.",
                                    ".domain.");
                }
                if (source.contains("org.springframework.context.annotation.Configuration")
                        || source.contains("@ConfigurationProperties")) {
                    assertThat(relative)
                            .as("configuration placement: %s", relative)
                            .startsWith("config/");
                }
                if (!composition
                        && (source.contains("org.springframework.http.")
                                || source.contains("org.springframework.security.web."))) {
                    assertThat(relative)
                            .as("HTTP adapter placement: %s", relative)
                            .matches("(?:asset/adapter/web|shared/error)/.*");
                }
                assertThat(source)
                        .as(relative)
                        .contains("package com.ecommerce.media"
                                + (relative.contains("/")
                                        ? "."
                                                + relative.substring(0, relative.lastIndexOf('/'))
                                                        .replace('/', '.')
                                        : "")
                                + ";");
            }
        }
        assertThat(Files.exists(sourceRoot.resolve("shared/error/GlobalExceptionHandler.java")))
                .isTrue();
        assertThat(Files.exists(sourceRoot.resolve("shared/error/ApiProblems.java")))
                .isTrue();
    }
}
