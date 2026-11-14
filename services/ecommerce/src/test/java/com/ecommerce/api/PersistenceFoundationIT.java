package com.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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

import com.ecommerce.api.category.application.exception.CategoryException;
import com.ecommerce.api.category.application.model.CategoryView;
import com.ecommerce.api.category.application.service.CategoryService;

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

    @Autowired
    CategoryService categoryService;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterEach
    void removeFixture() {
        jdbc.execute("DROP TABLE IF EXISTS ecommerce_it_transaction_fixture");
        jdbc.execute("TRUNCATE TABLE categories CASCADE");
        jdbc.update("UPDATE category_structure_state SET revision = 0 WHERE singleton_id = 1");
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

    @Test
    void migratesCategoryTablesAndAppliesOptimisticStructureRevision() {
        var actor = java.util.UUID.randomUUID();
        var first = categoryService.create(
                new CategoryService.Create("Đồ uống", null, null, null, null, null, null), actor);
        var second =
                categoryService.create(new CategoryService.Create("Đồ ăn", null, null, null, null, null, null), actor);
        assertThat(first.slug()).isEqualTo("do-uong");
        assertThat(first.status()).isEqualTo("INACTIVE");
        assertThat(first.visibility()).isEqualTo("PRIVATE");
        assertThat(second.structureRevision()).isEqualTo(first.structureRevision() + 1);

        var reordered = categoryService.reorder(
                new CategoryService.Reorder(
                        null, java.util.List.of(second.id(), first.id()), second.structureRevision()),
                actor);
        assertThat(reordered.structureRevision()).isEqualTo(second.structureRevision() + 1);
        assertThat(categoryService.get(second.id()).displayOrder()).isZero();
    }

    @Test
    void preservesCategoryVersionsAndEnforcesHierarchyCommands() {
        var actor = java.util.UUID.randomUUID();
        var otherActor = java.util.UUID.randomUUID();
        var root = categoryService.create(
                new CategoryService.Create(
                        "Đồ uống", null, "  ", java.util.UUID.randomUUID(), null, "ACTIVE", "PUBLIC"),
                actor);
        var destination = categoryService.create(
                new CategoryService.Create("Thực phẩm", null, null, null, null, "ACTIVE", "PUBLIC"), actor);
        var child = categoryService.create(
                new CategoryService.Create("Cà phê", null, null, null, root.id(), "ACTIVE", "PUBLIC"), actor);

        var snapshot = categoryService.snapshot();
        var childNode = snapshot.categories().stream()
                .filter(node -> node.category().id().equals(child.id()))
                .findFirst()
                .orElseThrow();
        assertThat(childNode.depth()).isEqualTo(2);
        assertThat(childNode.effectivelyVisible()).isTrue();

        assertThatThrownBy(() -> categoryService.move(
                        root.id(),
                        new CategoryService.Move(child.id(), root.version(), snapshot.structureRevision()),
                        otherActor))
                .isInstanceOfSatisfying(
                        CategoryException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CATEGORY_HIERARCHY"));

        var unchanged = categoryService.update(
                root.id(),
                new CategoryService.Update(
                        root.name(), root.slug(), null, "KEEP", null, root.status(), root.visibility(), root.version()),
                actor);
        assertThat(unchanged.version()).isEqualTo(root.version());
        assertThat(unchanged.updatedAt()).isEqualTo(root.updatedAt());

        var moved = categoryService.move(
                child.id(),
                new CategoryService.Move(destination.id(), child.version(), snapshot.structureRevision()),
                otherActor);
        assertThat(moved.category().parentId()).isEqualTo(destination.id());
        assertThat(moved.category().updatedBy()).isEqualTo(otherActor);
        assertThat(categoryService.get(root.id()).imageAssetId()).isEqualTo(root.imageAssetId());

        assertThatThrownBy(() -> categoryService.move(
                        root.id(),
                        new CategoryService.Move(
                                java.util.UUID.randomUUID(), root.version(), moved.structureRevision()),
                        actor))
                .isInstanceOf(com.ecommerce.api.category.application.exception.CategoryException.class)
                .hasMessageContaining("not found");

        categoryService.delete(child.id(), moved.category().version(), moved.structureRevision(), otherActor);
        assertThatThrownBy(() -> categoryService.get(child.id()))
                .isInstanceOf(com.ecommerce.api.category.application.exception.CategoryException.class);
    }

    @Test
    void concurrentCreatesAppendUniqueRootOrderAndAdvanceRevisionOncePerCreate() throws Exception {
        var actor = java.util.UUID.randomUUID();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await();
                return categoryService.create(
                        new CategoryService.Create("First", null, null, null, null, null, null), actor);
            });
            var second = pool.submit(() -> {
                start.await();
                return categoryService.create(
                        new CategoryService.Create("Second", null, null, null, null, null, null), actor);
            });
            start.countDown();
            var results = java.util.List.of(first.get(), second.get());
            assertThat(results).extracting(CategoryView::displayOrder).containsExactlyInAnyOrder(0, 1);
            assertThat(jdbc.queryForObject(
                            "SELECT revision FROM category_structure_state WHERE singleton_id = 1", Long.class))
                    .isEqualTo(2L);
        } finally {
            pool.shutdownNow();
        }
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
