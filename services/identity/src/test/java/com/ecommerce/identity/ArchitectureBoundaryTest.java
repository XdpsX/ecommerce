package com.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    @Test
    void keepsBusinessCodeIndependentOfAdaptersAndAuthorizationBehindAdminContracts() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/ecommerce/identity");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path :
                    paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                if (relative.startsWith("domain/") || relative.contains("/domain/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "org.springframework", ".application.", ".infrastructure.", ".api.", ".adapter.");
                }
                if (relative.startsWith("adapter/web/")) {
                    assertThat(source).as(relative).doesNotContain("org.springframework.jdbc", ".adapter.persistence.");
                }
                if (relative.startsWith("admin/application/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "org.springframework.jdbc",
                                    "org.springframework.web",
                                    "org.springframework.http",
                                    "com.ecommerce.identity.authorization",
                                    ".adapter.");
                }
                if (relative.startsWith("authorization/")) {
                    assertThat(source)
                            .as(relative)
                            .doesNotContain(
                                    "com.ecommerce.identity.admin.adapter.persistence",
                                    "com.ecommerce.identity.admin.application.port.AdministratorRepository",
                                    "com.ecommerce.identity.admin.application.port.BootstrapAdministratorStore");
                    assertThat(Pattern.compile(
                                            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(?:administrators|administrator_roles|administrator_audit|identity_security_state)\\b")
                                    .matcher(source)
                                    .find())
                            .as("%s must access admin-owned tables through AdministratorDirectory", relative)
                            .isFalse();
                }
                if (relative.startsWith("admin/adapter/web/") || relative.startsWith("admin/adapter/cli/")) {
                    assertThat(source).as(relative).doesNotContain(".adapter.persistence.", "org.springframework.jdbc");
                }
                assertThat(source)
                        .as(relative)
                        .contains("package com.ecommerce.identity"
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
