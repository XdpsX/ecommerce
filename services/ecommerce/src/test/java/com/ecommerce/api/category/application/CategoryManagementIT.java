package com.ecommerce.api.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.api.category.application.exception.CategoryException;
import com.ecommerce.api.category.application.model.CategoryData;
import com.ecommerce.api.category.application.model.CategoryFilter;
import com.ecommerce.api.category.application.model.CategoryPageData;
import com.ecommerce.api.category.application.port.CategoryPersistence;
import com.ecommerce.api.category.application.service.CategoryService;

@SpringBootTest
@Import(CategoryManagementIT.BarrierConfiguration.class)
@Testcontainers
class CategoryManagementIT {
    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CategoryService service;

    @Autowired
    BarrierCategoryPersistence persistence;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterEach
    void clearCategories() {
        persistence.clearBarriers();
        jdbc.update("DELETE FROM categories");
        jdbc.update("UPDATE category_structure_state SET revision = 0 WHERE singleton_id = 1");
    }

    @Test
    void movingMiddleRootCompactsOnlyFollowingSiblingsAndPreservesEarlierAudit() {
        UUID actor = UUID.randomUUID();
        var first = create("First", null, actor);
        var middle = create("Middle", null, actor);
        var last = create("Last", null, actor);
        var destination = create("Destination", null, actor);

        var result = service.move(
                middle.id(), new CategoryService.Move(destination.id(), middle.version(), revision()), actor);

        var firstAfter = service.get(first.id());
        var lastAfter = service.get(last.id());
        assertThat(firstAfter.displayOrder()).isZero();
        assertThat(firstAfter.version()).isEqualTo(first.version());
        assertThat(firstAfter.updatedAt()).isEqualTo(first.updatedAt());
        assertThat(firstAfter.updatedBy()).isEqualTo(first.updatedBy());
        assertThat(lastAfter.displayOrder()).isEqualTo(1);
        assertThat(lastAfter.version()).isEqualTo(last.version() + 1);
        assertThat(lastAfter.updatedBy()).isEqualTo(actor);
        assertThat(result.category().parentId()).isEqualTo(destination.id());
        assertThat(result.category().displayOrder()).isZero();
        assertThat(service.snapshot().categories())
                .extracting(node -> node.category().displayOrder())
                .containsExactly(0, 1, 2, 0);
    }

    @Test
    void detailAndListIncludeEffectiveVisibilityWithoutChangingStoredChildFlags() {
        UUID actor = UUID.randomUUID();
        var privateRoot = create("Private root", null, actor);
        var child = create("Public child", privateRoot.id(), actor, "ACTIVE", "PUBLIC");

        assertThat(service.get(child.id()).effectivelyVisible()).isFalse();
        var page = service.list(new CategoryService.Query(0, 20, null, null, null, null, false, null));
        var childFromList = page.getContent().stream()
                .filter(category -> category.id().equals(child.id()))
                .findFirst()
                .orElseThrow();
        assertThat(childFromList.effectivelyVisible()).isFalse();
        assertThat(childFromList.status()).isEqualTo("ACTIVE");
        assertThat(childFromList.visibility()).isEqualTo("PUBLIC");
    }

    @Test
    void reorderRejectsUnknownParentEvenForAnEmptySiblingList() {
        assertThatThrownBy(() -> service.reorder(
                        new CategoryService.Reorder(UUID.randomUUID(), List.of(), revision()), UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        CategoryException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CATEGORY_NOT_FOUND"));
    }

    @Test
    void reorderAllowsExistingParentWithoutChildrenAndEmptyRootGroupAsNoOps() {
        UUID actor = UUID.randomUUID();
        var emptyRoot = service.reorder(new CategoryService.Reorder(null, List.of(), revision()), actor);
        assertThat(emptyRoot.structureRevision()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT revision FROM category_structure_state WHERE singleton_id = 1", Long.class))
                .isZero();

        var parent = create("Empty parent", null, actor);
        long beforeRevision = revision();
        var result = service.reorder(new CategoryService.Reorder(parent.id(), List.of(), beforeRevision), actor);

        var parentAfter = persistence.findById(parent.id()).orElseThrow();
        assertThat(result.structureRevision()).isEqualTo(beforeRevision);
        assertThat(parentAfter.version()).isEqualTo(parent.version());
        assertThat(parentAfter.updatedAt()).isEqualTo(parent.updatedAt());
        assertThat(parentAfter.updatedBy()).isEqualTo(parent.updatedBy());
        assertThat(revision()).isEqualTo(beforeRevision);
    }

    @Test
    void acceptsDepthEightAndRejectsDepthNineWithoutChangingTreeOrRevision() {
        UUID actor = UUID.randomUUID();
        CategoryData parent = null;
        for (int depth = 1; depth <= 8; depth++) {
            parent = create("Depth " + depth, parent == null ? null : parent.id(), actor);
        }
        CategoryData depthEight = parent;
        long beforeRevision = revision();

        assertThatThrownBy(() -> service.create(
                        new CategoryService.Create("Depth 9", null, null, null, depthEight.id(), null, null), actor))
                .isInstanceOfSatisfying(
                        CategoryException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CATEGORY_DEPTH_EXCEEDED"));

        assertThat(service.snapshot().categories()).hasSize(8);
        assertThat(revision()).isEqualTo(beforeRevision);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class))
                .isEqualTo(8);
    }

    @Test
    void rejectsMovingSubtreePastDepthLimitWithoutChangingAnyRowsOrRevision() {
        UUID actor = UUID.randomUUID();
        var source = create("Source", null, actor);
        var moving = create("Moving subtree", source.id(), actor);
        var descendant = create("Subtree descendant", moving.id(), actor);
        CategoryData destination = null;
        for (int depth = 1; depth <= 7; depth++) {
            destination = create("Destination depth " + depth, destination == null ? null : destination.id(), actor);
        }
        CategoryData deepestDestination = destination;
        List<CategoryData> before = persistence.findAll();
        long beforeRevision = revision();

        assertThatThrownBy(() -> service.move(
                        moving.id(),
                        new CategoryService.Move(deepestDestination.id(), moving.version(), beforeRevision),
                        actor))
                .isInstanceOfSatisfying(
                        CategoryException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CATEGORY_DEPTH_EXCEEDED"));

        assertThat(persistence.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(revision()).isEqualTo(beforeRevision);
        assertThat(service.get(moving.id()).parentId()).isEqualTo(source.id());
        assertThat(service.get(descendant.id()).parentId()).isEqualTo(moving.id());
    }

    @Test
    void createExhaustsThreeStructureRetriesAndLeavesNoPartialCategoryOrRevision() {
        UUID actor = UUID.randomUUID();
        var existing = create("Existing", null, actor);
        long beforeRevision = revision();
        persistence.rejectNextCompareAndAdvanceCalls(3);

        assertThatThrownBy(() -> service.create(
                        new CategoryService.Create("Never committed", null, null, null, null, null, null), actor))
                .isInstanceOfSatisfying(
                        CategoryException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CATEGORY_STRUCTURE_CONFLICT"));

        assertThat(persistence.rejectedCompareAndAdvanceCalls()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class))
                .isEqualTo(1);
        assertThat(persistence.findById(existing.id())).contains(existing);
        assertThat(revision()).isEqualTo(beforeRevision);
    }

    @Test
    void oppositeMovesValidatedAgainstSameRevisionCannotCreateCycle() throws Exception {
        UUID actor = UUID.randomUUID();
        var first = create("First", null, actor);
        var second = create("Second", null, actor);
        long expectedRevision = revision();
        persistence.armCompareAndAdvanceBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var moveFirst = pool.submit(() -> service.move(
                    first.id(), new CategoryService.Move(second.id(), first.version(), expectedRevision), actor));
            var moveSecond = pool.submit(() -> service.move(
                    second.id(), new CategoryService.Move(first.id(), second.version(), expectedRevision), actor));
            var outcomes = List.of(outcome(moveFirst), outcome(moveSecond));
            assertThat(outcomes.stream().filter(CategoryException.class::isInstance))
                    .hasSize(1);
            assertThat(outcomes.stream().filter(CategoryService.StructureResult.class::isInstance))
                    .hasSize(1);
        }

        var snapshot = service.snapshot();
        assertThat(snapshot.categories()).hasSize(2);
        assertThat(snapshot.categories())
                .allSatisfy(node -> assertThat(node.depth()).isBetween(1, 2));
        assertThat(snapshot.structureRevision()).isEqualTo(expectedRevision + 1);
    }

    @Test
    void concurrentCreatesForceCasRetryAndCommitBothAppends() throws Exception {
        UUID actor = UUID.randomUUID();
        persistence.armCompareAndAdvanceBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> create("Concurrent A", null, actor));
            var second = pool.submit(() -> create("Concurrent B", null, actor));
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results).extracting(CategoryData::displayOrder).containsExactlyInAnyOrder(0, 1);
        }
        assertThat(revision()).isEqualTo(2L);
    }

    @Test
    void staleConcurrentMetadataUpdateBecomesVersionConflict() throws Exception {
        UUID actor = UUID.randomUUID();
        var category =
                service.create(new CategoryService.Create("Original", null, null, null, null, null, null), actor);
        persistence.armSaveBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.update(
                    category.id(),
                    new CategoryService.Update(
                            "Renamed A",
                            "original",
                            null,
                            "KEEP",
                            null,
                            category.status(),
                            category.visibility(),
                            category.version()),
                    actor));
            var second = pool.submit(() -> service.update(
                    category.id(),
                    new CategoryService.Update(
                            "Renamed B",
                            "original",
                            null,
                            "KEEP",
                            null,
                            category.status(),
                            category.visibility(),
                            category.version()),
                    actor));
            var outcomes = List.of(outcome(first), outcome(second));
            assertThat(outcomes.stream().filter(CategoryException.class::isInstance))
                    .hasSize(1);
            assertThat(outcomes.stream()
                            .filter(com.ecommerce.api.category.application.model.CategoryView.class::isInstance))
                    .hasSize(1);
            assertThat(((CategoryException) outcomes.stream()
                                    .filter(CategoryException.class::isInstance)
                                    .findFirst()
                                    .orElseThrow())
                            .code())
                    .isEqualTo("CATEGORY_VERSION_CONFLICT");
        }
        assertThat(service.get(category.id()).version()).isEqualTo(category.version() + 1);
    }

    @Test
    void deleteAndReorderAgainstSameRevisionCommitAtMostOneStructuralCommand() throws Exception {
        UUID actor = UUID.randomUUID();
        var first = create("First", null, actor);
        var second = create("Second", null, actor);
        var third = create("Third", null, actor);
        long expectedRevision = revision();
        persistence.armCompareAndAdvanceBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var delete = pool.submit(() -> {
                service.delete(first.id(), first.version(), expectedRevision, actor);
                return "deleted";
            });
            var reorder = pool.submit(() -> service.reorder(
                    new CategoryService.Reorder(null, List.of(third.id(), second.id(), first.id()), expectedRevision),
                    actor));
            var outcomes = new Object[] {outcome(delete), outcome(reorder)};
            assertThat(java.util.Arrays.stream(outcomes).filter(CategoryException.class::isInstance))
                    .hasSize(1);
            assertThat(revision()).isEqualTo(expectedRevision + 1);
        }

        var roots = service.snapshot().categories().stream()
                .filter(node -> node.category().parentId() == null)
                .map(node -> node.category().displayOrder())
                .sorted()
                .toList();
        assertThat(roots)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, roots.size())
                        .boxed()
                        .toList());
        assertThat(roots).hasSizeBetween(2, 3);
    }

    @Test
    void createAndDeleteParentRaceCannotLeaveAnOrphanOrDeleteAParentWithNewChild() throws Exception {
        UUID actor = UUID.randomUUID();
        var parent = create("Parent", null, actor);
        long expectedRevision = revision();
        persistence.armCompareAndAdvanceBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var createChild = pool.submit(() -> service.create(
                    new CategoryService.Create("Child", null, null, null, parent.id(), null, null), actor));
            var deleteParent = pool.submit(() -> {
                service.delete(parent.id(), parent.version(), expectedRevision, actor);
                return "deleted";
            });
            Object[] outcomes = {outcome(createChild), outcome(deleteParent)};
            assertThat(java.util.Arrays.stream(outcomes).filter(CategoryException.class::isInstance))
                    .hasSize(1);
            assertThat(revision()).isEqualTo(expectedRevision + 1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class))
                .isIn(0, 2);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, parent.id()) == 0) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class))
                    .isZero();
        } else {
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM categories WHERE parent_id = ?", Integer.class, parent.id()))
                    .isEqualTo(1);
        }
    }

    @Test
    void moveAndReorderAgainstSameRevisionCannotPersistBothPlans() throws Exception {
        UUID actor = UUID.randomUUID();
        var first = create("First", null, actor);
        var moving = create("Moving", null, actor);
        var destination = create("Destination", null, actor);
        long expectedRevision = revision();
        persistence.armCompareAndAdvanceBarrier(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var move = pool.submit(() -> service.move(
                    moving.id(),
                    new CategoryService.Move(destination.id(), moving.version(), expectedRevision),
                    actor));
            var reorder = pool.submit(() -> service.reorder(
                    new CategoryService.Reorder(
                            null, List.of(destination.id(), moving.id(), first.id()), expectedRevision),
                    actor));
            Object[] outcomes = {outcome(move), outcome(reorder)};
            assertThat(java.util.Arrays.stream(outcomes).filter(CategoryException.class::isInstance))
                    .hasSize(1);
            assertThat(revision()).isEqualTo(expectedRevision + 1);
        }

        var tree = service.snapshot();
        var movingAfter = service.get(moving.id());
        if (movingAfter.parentId() == null) {
            var rootOrders = tree.categories().stream()
                    .filter(node -> node.category().parentId() == null)
                    .map(node -> node.category().displayOrder())
                    .sorted()
                    .toList();
            assertThat(rootOrders).containsExactly(0, 1, 2);
        } else {
            assertThat(movingAfter.parentId()).isEqualTo(destination.id());
            var rootOrders = tree.categories().stream()
                    .filter(node -> node.category().parentId() == null)
                    .map(node -> node.category().displayOrder())
                    .sorted()
                    .toList();
            assertThat(rootOrders).containsExactly(0, 1);
        }
    }

    @Test
    void snapshotRevisionAndRowsComeFromOneRepeatableReadSnapshot() throws Exception {
        UUID actor = UUID.randomUUID();
        create("Before snapshot", null, actor);
        long revisionBefore = revision();
        persistence.pauseNextFindAll();

        try (var pool = Executors.newSingleThreadExecutor()) {
            var snapshotFuture = pool.submit(service::snapshot);
            assertThat(persistence.awaitPausedRead()).isTrue();
            create("Committed during snapshot", null, actor);
            persistence.releasePausedRead();
            var snapshot = snapshotFuture.get(10, TimeUnit.SECONDS);
            assertThat(snapshot.structureRevision()).isEqualTo(revisionBefore);
            assertThat(snapshot.categories()).hasSize(1);
        } finally {
            persistence.releasePausedRead();
        }
    }

    private Object outcome(java.util.concurrent.Future<?> future) throws Exception {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException exception) {
            return exception.getCause();
        }
    }

    private CategoryData create(String name, UUID parent, UUID actor) {
        var view = service.create(new CategoryService.Create(name, null, null, null, parent, null, null), actor);
        return persistence.findById(view.id()).orElseThrow();
    }

    private CategoryData create(String name, UUID parent, UUID actor, String status, String visibility) {
        var view =
                service.create(new CategoryService.Create(name, null, null, null, parent, status, visibility), actor);
        return persistence.findById(view.id()).orElseThrow();
    }

    private long revision() {
        return jdbc.queryForObject("SELECT revision FROM category_structure_state WHERE singleton_id = 1", Long.class);
    }

    @TestConfiguration
    static class BarrierConfiguration {
        @Bean
        @Primary
        BarrierCategoryPersistence categoryPersistenceBarrier(
                @Qualifier("jpaCategoryPersistence") CategoryPersistence delegate) {
            return new BarrierCategoryPersistence(delegate);
        }
    }

    static final class BarrierCategoryPersistence implements CategoryPersistence {
        private final CategoryPersistence delegate;
        private final AtomicReference<CyclicBarrier> compareBarrier = new AtomicReference<>();
        private final AtomicInteger compareParticipants = new AtomicInteger();
        private final AtomicReference<CyclicBarrier> saveBarrier = new AtomicReference<>();
        private final AtomicInteger saveParticipants = new AtomicInteger();
        private final AtomicInteger forcedCompareFailures = new AtomicInteger();
        private final AtomicInteger rejectedCompareCalls = new AtomicInteger();
        private final AtomicReference<ReadPause> readPause = new AtomicReference<>();
        private final AtomicReference<ReadPause> activeReadPause = new AtomicReference<>();
        private final AtomicReference<ReadPause> latestReadPause = new AtomicReference<>();

        BarrierCategoryPersistence(CategoryPersistence delegate) {
            this.delegate = delegate;
        }

        void armCompareAndAdvanceBarrier(int parties) {
            compareParticipants.set(parties);
            compareBarrier.set(new CyclicBarrier(parties));
        }

        void armSaveBarrier(int parties) {
            saveParticipants.set(parties);
            saveBarrier.set(new CyclicBarrier(parties));
        }

        void rejectNextCompareAndAdvanceCalls(int attempts) {
            rejectedCompareCalls.set(0);
            forcedCompareFailures.set(attempts);
        }

        int rejectedCompareAndAdvanceCalls() {
            return rejectedCompareCalls.get();
        }

        void clearBarriers() {
            compareBarrier.set(null);
            compareParticipants.set(0);
            forcedCompareFailures.set(0);
            rejectedCompareCalls.set(0);
            saveBarrier.set(null);
            saveParticipants.set(0);
            releasePausedRead();
            readPause.set(null);
            activeReadPause.set(null);
            latestReadPause.set(null);
        }

        void pauseNextFindAll() {
            ReadPause pause = new ReadPause();
            latestReadPause.set(pause);
            readPause.set(pause);
        }

        boolean awaitPausedRead() throws InterruptedException {
            ReadPause pause = latestReadPause.get();
            return pause != null && pause.readFinished.await(5, TimeUnit.SECONDS);
        }

        void releasePausedRead() {
            ReadPause pause = activeReadPause.getAndSet(null);
            if (pause == null) pause = readPause.getAndSet(null);
            if (pause != null) pause.release.countDown();
        }

        @Override
        public List<CategoryData> findAll() {
            List<CategoryData> categories = delegate.findAll();
            ReadPause pause = readPause.getAndSet(null);
            if (pause != null) {
                activeReadPause.set(pause);
                pause.readFinished.countDown();
                try {
                    if (!pause.release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to resume a category snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while pausing a category snapshot.", exception);
                }
            }
            return categories;
        }

        @Override
        public CategoryPageData findPage(CategoryFilter filter, int page, int size) {
            return delegate.findPage(filter, page, size);
        }

        @Override
        public boolean hierarchyWithinDepth(int maximumDepth) {
            return delegate.hierarchyWithinDepth(maximumDepth);
        }

        @Override
        public Map<UUID, Boolean> effectiveVisibilityFor(List<UUID> categoryIds) {
            return delegate.effectiveVisibilityFor(categoryIds);
        }

        @Override
        public java.util.Optional<CategoryData> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public CategoryData saveAndFlush(CategoryData category) {
            CyclicBarrier barrier = saveBarrier.get();
            if (barrier != null) {
                int remaining = saveParticipants.decrementAndGet();
                if (remaining <= 0) saveBarrier.compareAndSet(barrier, null);
                await(barrier);
            }
            return delegate.saveAndFlush(category);
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void delete(CategoryData category) {
            delegate.delete(category);
        }

        @Override
        public long currentStructureRevision() {
            return delegate.currentStructureRevision();
        }

        @Override
        public boolean compareAndAdvanceStructureRevision(long expected) {
            int forcedFailures = forcedCompareFailures.getAndUpdate(value -> Math.max(0, value - 1));
            if (forcedFailures > 0) {
                rejectedCompareCalls.incrementAndGet();
                return false;
            }
            CyclicBarrier barrier = compareBarrier.get();
            if (barrier != null) {
                int remaining = compareParticipants.decrementAndGet();
                if (remaining <= 0) compareBarrier.compareAndSet(barrier, null);
                await(barrier);
            }
            return delegate.compareAndAdvanceStructureRevision(expected);
        }

        private static void await(CyclicBarrier barrier) {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Timed out waiting for concurrent category commands.", exception);
            }
        }

        private static final class ReadPause {
            private final CountDownLatch readFinished = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);
        }
    }
}
