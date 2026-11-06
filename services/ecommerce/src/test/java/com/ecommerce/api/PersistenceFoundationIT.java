package com.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PersistenceFoundationIT {
    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterEach
    void removeFixture() {
        jdbc.execute("DROP TABLE IF EXISTS ecommerce_it_transaction_fixture");
    }

    @Test
    void appliesVersionOneAndUsesTheConfiguredTransactionManager() {
        var migration = java.util.Arrays.stream(flyway.info().applied())
                .filter(applied -> "1".equals(applied.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();

        assertThat(migration.getDescription()).isEqualTo("ecommerce foundation");
        assertThat(migration.getChecksum()).isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT checksum FROM flyway_schema_history WHERE version = '1' AND success = true",
                        Integer.class))
                .isEqualTo(migration.getChecksum());
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        jdbc.execute("CREATE TABLE ecommerce_it_transaction_fixture (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
        jdbc.update("INSERT INTO ecommerce_it_transaction_fixture (id, value) VALUES (1, 'seed')");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    jdbc.update("UPDATE ecommerce_it_transaction_fixture SET value = 'changed' WHERE id = 1");
                    jdbc.update("INSERT INTO ecommerce_it_transaction_fixture (id, value) VALUES (2, 'rolled back')");
                    assertThat(fixtureValue(1)).isEqualTo("changed");
                    assertThat(fixtureCount(2)).isEqualTo(1);
                    throw new DeliberateRollback();
                }))
                .isInstanceOf(DeliberateRollback.class);

        assertThat(fixtureValue(1)).isEqualTo("seed");
        assertThat(fixtureCount(2)).isZero();

        transaction.executeWithoutResult(
                status -> jdbc.update("UPDATE ecommerce_it_transaction_fixture SET value = 'committed' WHERE id = 1"));
        assertThat(fixtureValue(1)).isEqualTo("committed");
    }

    private String fixtureValue(int id) {
        return jdbc.queryForObject("SELECT value FROM ecommerce_it_transaction_fixture WHERE id = ?", String.class, id);
    }

    private int fixtureCount(int id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecommerce_it_transaction_fixture WHERE id = ?", Integer.class, id);
    }

    private static final class DeliberateRollback extends RuntimeException {}
}
