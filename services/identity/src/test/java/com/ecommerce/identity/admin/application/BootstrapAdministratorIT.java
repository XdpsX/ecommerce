package com.ecommerce.identity.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.identity.admin.application.exception.AdministratorException;

@SpringBootTest(properties = "identity.server-enabled=false")
@Testcontainers
class BootstrapAdministratorIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    BootstrapAdministratorService bootstrapService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("UPDATE identity_security_state SET bootstrap_completed_at = NULL, "
                + "bootstrap_administrator_id = NULL WHERE singleton_id = 1");
        jdbcTemplate.update("DELETE FROM administrator_audit");
        jdbcTemplate.update("DELETE FROM administrator_roles");
        jdbcTemplate.update("DELETE FROM administrators");
    }

    @Test
    void repeatDoesNotOverwriteTheInitialAccount() {
        char[] original = "first-bootstrap-password".toCharArray();
        char[] repeated = "different-bootstrap-password".toCharArray();

        assertThat(bootstrapService.bootstrap("root.operator", original)).isTrue();
        assertThat(bootstrapService.bootstrap("replacement.operator", repeated)).isFalse();

        assertThat(original).containsOnly('\0');
        assertThat(repeated).containsOnly('\0');
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM administrators", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT username FROM administrators", String.class))
                .isEqualTo("root.operator");
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM administrators", String.class);
        assertThat(passwordEncoder.matches("first-bootstrap-password", hash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT bootstrap_completed_at IS NOT NULL FROM identity_security_state WHERE singleton_id = 1",
                        Boolean.class))
                .isTrue();
    }

    @Test
    void simultaneousBootstrapCreatesOnlyOneInitialAdministrator() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> bootstrapAfterBarrier("first", ready, start));
            var second = executor.submit(() -> bootstrapAfterBarrier("second", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM administrators", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM administrator_audit", Integer.class))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refusesToAdoptAnExistingAdministratorWhenMarkerIsMissing() {
        jdbcTemplate.update(
                "INSERT INTO administrators(id, username, password_hash) VALUES (?, ?, ?)",
                UUID.randomUUID(),
                "existing.operator",
                "already-hashed");

        assertThatThrownBy(() -> bootstrapService.bootstrap("new.operator", "new-admin-password".toCharArray()))
                .isInstanceOf(AdministratorException.class)
                .hasMessageContaining("without a completed bootstrap marker");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM administrators", Integer.class))
                .isEqualTo(1);
    }

    private boolean bootstrapAfterBarrier(String username, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return bootstrapService.bootstrap(username, (username + "-bootstrap-password").toCharArray());
    }
}
