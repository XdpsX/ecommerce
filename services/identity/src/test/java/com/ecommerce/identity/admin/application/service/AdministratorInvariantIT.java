package com.ecommerce.identity.admin.application.service;

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
import com.ecommerce.identity.admin.application.exception.LastAdministratorException;

@SpringBootTest(properties = "identity.server-enabled=false")
@Testcontainers
class AdministratorInvariantIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    AdministratorService administratorService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID first;
    private UUID second;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM administrator_audit");
        jdbcTemplate.update("DELETE FROM administrator_roles");
        jdbcTemplate.update("DELETE FROM administrators");
    }

    @Test
    void serializesConcurrentAttemptsToDisableTheOtherLastAdministrators() throws Exception {
        first = insertPrivilegedAdmin("first");
        second = insertPrivilegedAdmin("second");
        assertConcurrentChangesKeepAnAdministrator(
                (actor, target) -> administratorService.setEnabled(actor, target, false));
    }

    @Test
    void serializesConcurrentAttemptsToDemoteTheOtherLastAdministrators() throws Exception {
        first = insertPrivilegedAdmin("first-demotion");
        second = insertPrivilegedAdmin("second-demotion");
        assertConcurrentChangesKeepAnAdministrator(
                (actor, target) -> administratorService.replaceRoles(actor, target, java.util.Set.of()));
    }

    @Test
    void serializesConcurrentDisableAndDemotionOfTheOtherLastAdministrators() throws Exception {
        first = insertPrivilegedAdmin("first-mixed");
        second = insertPrivilegedAdmin("second-mixed");
        assertConcurrentChangesKeepAnAdministrator((actor, target) -> {
            if (actor.equals(first)) {
                administratorService.setEnabled(actor, target, false);
            } else {
                administratorService.replaceRoles(actor, target, java.util.Set.of());
            }
        });
    }

    private void assertConcurrentChangesKeepAnAdministrator(PrivilegedChange change) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> changeAfterBarrier(first, second, ready, start, change));
            var two = executor.submit(() -> changeAfterBarrier(second, first, ready, start, change));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
            assertThat(countPrivilegedAdministrators()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsDisablingTheOnlyPrivilegedAdministratorAndRollsBack() {
        first = insertPrivilegedAdmin("only-admin");

        assertThatThrownBy(() -> administratorService.setEnabled(first, first, false))
                .isInstanceOf(LastAdministratorException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT enabled FROM administrators WHERE id = ?", Boolean.class, first))
                .isTrue();
    }

    @Test
    void createsNormalizedAccountWithAnEncodedPassword() {
        first = insertPrivilegedAdmin("operator");
        String rawPassword = "a-long-admin-password";

        var created = administratorService.create(first, "  New.Admin  ", rawPassword);

        assertThat(created.username()).isEqualTo("new.admin");
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM administrators WHERE id = ?", String.class, created.id());
        assertThat(storedHash).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, storedHash)).isTrue();
    }

    private boolean changeAfterBarrier(
            UUID actor, UUID target, CountDownLatch ready, CountDownLatch start, PrivilegedChange change)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            change.apply(actor, target);
            return true;
        } catch (AdministratorException exception) {
            return false;
        }
    }

    private UUID insertPrivilegedAdmin(String username) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                id,
                username,
                passwordEncoder.encode("fixture-password"));
        jdbcTemplate.update(
                "INSERT INTO administrator_roles(administrator_id, role_id) "
                        + "SELECT ?, id FROM roles WHERE code = 'SUPER_ADMIN'",
                id);
        return id;
    }

    private int countPrivilegedAdministrators() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT a.id) FROM administrators a "
                        + "JOIN administrator_roles ar ON ar.administrator_id = a.id "
                        + "JOIN roles r ON r.id = ar.role_id "
                        + "WHERE a.enabled AND r.code = 'SUPER_ADMIN'",
                Integer.class);
    }

    @FunctionalInterface
    private interface PrivilegedChange {
        void apply(UUID actor, UUID target);
    }
}
