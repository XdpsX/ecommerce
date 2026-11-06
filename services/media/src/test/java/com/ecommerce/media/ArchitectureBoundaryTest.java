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
                if (relative.startsWith("asset/domain/")) {
                    assertThat(source).as(relative).doesNotContain("org.springframework", ".application.", ".adapter.");
                }
                if (relative.startsWith("asset/application/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    ".adapter.",
                                    "org.springframework.http",
                                    "org.springframework.jdbc",
                                    "com.cloudinary");
                }
                if (relative.startsWith("asset/adapter/web/")) {
                    assertThat(source).as(relative).doesNotContain("org.springframework.jdbc", ".adapter.persistence.");
                }
                if (relative.startsWith("adapter/web/")) {
                    assertThat(source).as(relative).doesNotContain("org.springframework.jdbc", ".adapter.persistence.");
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
    }
}
