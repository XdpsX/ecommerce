package com.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    @Test
    void keepsHttpAdaptersAtTheWebBoundaryAndTheApplicationEntryPointFreeOfHttpDetails() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/ecommerce/api");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path :
                    paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path);
                if (source.contains("org.springframework.web.")
                        || source.contains("org.springframework.http.")
                        || source.contains("org.springframework.security.web.")
                        || source.contains("jakarta.servlet.")) {
                    assertThat(relative).as("HTTP boundary: %s", relative).startsWith("adapter/web/");
                }
                if (relative.startsWith("adapter/web/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain("org.springframework.jdbc", "jakarta.persistence", ".adapter.persistence.");
                }
                String packageSuffix = relative.contains("/")
                        ? "." + relative.substring(0, relative.lastIndexOf('/')).replace('/', '.')
                        : "";
                assertThat(source).as(relative).contains("package com.ecommerce.api" + packageSuffix + ";");
            }
        }
    }
}
