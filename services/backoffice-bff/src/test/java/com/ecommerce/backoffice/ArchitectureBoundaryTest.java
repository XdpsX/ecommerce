package com.ecommerce.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    @Test
    void keepsSpringCompositionInConfigAndSharedBoundaryComponentsInSharedPackages() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/ecommerce/backoffice");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path :
                    paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path);
                if (source.contains("org.springframework.context.annotation.Configuration")
                        || source.contains("@ConfigurationProperties")) {
                    assertThat(relative)
                            .as("configuration placement: %s", relative)
                            .startsWith("config/");
                }
                if (source.contains("implements WebFilter")
                        || source.contains("implements ServerOAuth2AuthorizationRequestResolver")) {
                    assertThat(relative)
                            .as("web implementation placement: %s", relative)
                            .startsWith("shared/security/");
                }
                if (relative.endsWith("/ApiProblems.java") || relative.endsWith("/RestErrorHandler.java")) {
                    assertThat(relative)
                            .as("HTTP error placement: %s", relative)
                            .startsWith("shared/error/");
                }
                if (relative.startsWith("shared/security/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "org.springframework.jdbc",
                                    "jakarta.persistence",
                                    ".adapter.",
                                    ".config.",
                                    ".session.");
                }
                if (relative.startsWith("shared/error/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "org.springframework.jdbc",
                                    "jakarta.persistence",
                                    ".adapter.",
                                    ".config.",
                                    ".session.",
                                    ".shared.security.");
                }
                String packageSuffix = relative.contains("/")
                        ? "." + relative.substring(0, relative.lastIndexOf('/')).replace('/', '.')
                        : "";
                assertThat(source).as(relative).contains("package com.ecommerce.backoffice" + packageSuffix + ";");
            }
        }
        assertThat(Files.exists(sourceRoot.resolve("configuration"))).isFalse();
        assertThat(Files.exists(sourceRoot.resolve("shared/security/BrowserBoundaryFilter.java")))
                .isTrue();
        assertThat(Files.exists(sourceRoot.resolve("shared/security/SessionExpiryFilter.java")))
                .isTrue();
        assertThat(Files.exists(sourceRoot.resolve("shared/security/LocaleAwareAuthorizationRequestResolver.java")))
                .isTrue();
        assertThat(Files.exists(sourceRoot.resolve("shared/error/RestErrorHandler.java")))
                .isTrue();
        assertThat(Files.exists(sourceRoot.resolve("shared/error/ApiProblems.java")))
                .isTrue();
    }
}
