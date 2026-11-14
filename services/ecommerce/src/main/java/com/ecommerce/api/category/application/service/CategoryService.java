package com.ecommerce.api.category.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.api.category.application.exception.CategoryException;
import com.ecommerce.api.category.application.model.CategoryData;
import com.ecommerce.api.category.application.model.CategoryFilter;
import com.ecommerce.api.category.application.model.CategoryLimits;
import com.ecommerce.api.category.application.model.CategoryPageData;
import com.ecommerce.api.category.application.model.CategoryView;
import com.ecommerce.api.category.application.port.CategoryPersistence;
import com.ecommerce.api.category.domain.service.CategorySlugPolicy;

@Service
public class CategoryService {
    private final CategoryPersistence categories;
    private final CategoryLimits limits;
    private final PlatformTransactionManager transactionManager;

    public CategoryService(
            CategoryPersistence categories, CategoryLimits limits, PlatformTransactionManager transactionManager) {
        this.categories = categories;
        this.limits = limits;
        this.transactionManager = transactionManager;
    }

    public CategoryView create(Create input, UUID actor) {
        UUID id = UUID.randomUUID();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                return transaction.execute(status -> createAttempt(input, actor, id));
            } catch (CategoryException exception) {
                if (!exception.code().equals("CATEGORY_STRUCTURE_CONFLICT") || attempt == 2) throw exception;
            }
        }
        throw structureConflict();
    }

    private CategoryView createAttempt(Create input, UUID actor, UUID id) {
        String name = cleanName(input.name());
        String slug = normalizeSlug(input.slug(), name);
        long revision = currentRevision();
        List<CategoryData> tree = categories.findAll();
        validateConfiguredDepth(tree);
        int parentDepth = input.parentId() == null ? 0 : depthOf(tree, require(tree, input.parentId()));
        ensureDepth(parentDepth + 1);
        UUID parent = input.parentId();
        int order = siblings(tree, parent).size();
        if (!advanceRevision(revision)) throw structureConflict();
        Instant now = now();
        CategoryData saved = categories.saveAndFlush(CategoryData.create(
                id,
                parent,
                name,
                slug,
                cleanDescription(input.description()),
                input.imageAssetId(),
                status(input.status(), "INACTIVE"),
                visibility(input.visibility(), "PRIVATE"),
                order,
                now,
                actor));
        return view(saved, revision + 1, effectiveVisibility(saved.id()));
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> list(Query query) {
        validatePage(query.page(), query.size());
        if (query.status() != null && !Set.of("ACTIVE", "INACTIVE").contains(query.status())) throw invalid("status");
        if (query.visibility() != null && !Set.of("PUBLIC", "PRIVATE").contains(query.visibility()))
            throw invalid("visibility");
        if ((query.rootId() != null && (query.parentId() != null || query.rootsOnly()))
                || (query.parentId() != null && query.rootsOnly())) throw invalid("rootId/parentId");
        String term = query.q() == null ? null : query.q().trim().toLowerCase(java.util.Locale.ROOT);
        long revision = currentRevision();
        verifyConfiguredDepth();
        CategoryPageData result = categories.findPage(
                new CategoryFilter(
                        term, query.status(), query.visibility(), query.parentId(), query.rootsOnly(), query.rootId()),
                query.page(),
                query.size());
        Map<UUID, Boolean> effectiveVisibility = categories.effectiveVisibilityFor(
                result.content().stream().map(CategoryData::id).toList());
        List<CategoryView> content = result.content().stream()
                .map(c -> view(c, revision, effectiveVisibility.getOrDefault(c.id(), false)))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                content, PageRequest.of(result.page(), result.size()), result.totalElements());
    }

    @Transactional(readOnly = true)
    public CategoryView get(UUID id) {
        return view(find(id), currentRevision());
    }

    @Transactional(readOnly = true)
    public String suggest(String name) {
        if (name == null || name.isBlank()) throw invalid("name");
        return CategorySlugPolicy.suggest(name.trim());
    }

    @Transactional
    public CategoryView update(UUID id, Update input, UUID actor) {
        CategoryData current = find(id);
        checkVersion(current, input.expectedVersion());
        String name = cleanName(input.name());
        String slug = normalizeSlug(input.slug(), name);
        UUID imageId = switch (input.imageAction() == null ? "KEEP" : input.imageAction()) {
            case "KEEP" -> {
                if (input.imageAssetId() != null) throw invalid("imageAssetId");
                yield current.imageAssetId();
            }
            case "SET" -> {
                if (input.imageAssetId() == null) throw invalid("imageAssetId");
                yield input.imageAssetId();
            }
            case "REMOVE" -> {
                if (input.imageAssetId() != null) throw invalid("imageAssetId");
                yield null;
            }
            default -> throw invalid("imageAction");
        };
        current = current.update(
                name,
                slug,
                cleanDescription(input.description()),
                imageId,
                status(input.status(), null),
                visibility(input.visibility(), null),
                now(),
                actor);
        current = categories.saveAndFlush(current);
        return view(current, currentRevision());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot() {
        List<CategoryData> all = categories.findAll().stream()
                .sorted(Comparator.comparing(CategoryData::parentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingInt(CategoryData::displayOrder))
                .toList();
        validateConfiguredDepth(all);
        long revision = currentRevision();
        List<TreeNode> nodes = new ArrayList<>();
        appendTree(all, null, nodes, revision);
        return new Snapshot(revision, nodes);
    }

    @Transactional
    public StructureResult move(UUID id, Move input, UUID actor) {
        long revision = currentRevision();
        checkRevision(input.expectedStructureRevision(), revision);
        List<CategoryData> tree = categories.findAll();
        validateConfiguredDepth(tree);
        CategoryData target = require(tree, id);
        checkVersion(target, input.expectedVersion());
        if (Objects.equals(target.parentId(), input.parentId()))
            return new StructureResult(revision, view(target, revision));
        CategoryData parent = input.parentId() == null ? null : require(tree, input.parentId());
        Set<UUID> descendants = descendants(tree, id);
        if (Objects.equals(id, input.parentId()) || descendants.contains(input.parentId()))
            throw new CategoryException(
                    CategoryException.Kind.INVALID_CATEGORY_HIERARCHY, "The requested parent creates a cycle.");
        int newParentDepth = parent == null ? 0 : depthOf(tree, parent);
        int subtreeHeight = subtreeHeight(tree, id);
        ensureDepth(newParentDepth + subtreeHeight);
        if (!advanceRevision(revision)) throw structureConflict();
        List<CategoryData> source = siblings(tree, target.parentId());
        List<CategoryData> destination = siblings(tree, input.parentId());
        source.remove(target);
        Instant now = now();
        for (int index = 0; index < source.size(); index++) {
            CategoryData category = source.get(index);
            if (category.displayOrder() != index) {
                categories.saveAndFlush(compact(category, index, actor, now));
            }
        }
        int next = destination.size();
        target = categories.saveAndFlush(target.move(input.parentId(), next, now, actor));
        categories.flush();
        return new StructureResult(revision + 1, view(target, revision + 1));
    }

    @Transactional
    public StructureResult reorder(Reorder input, UUID actor) {
        long revision = currentRevision();
        checkRevision(input.expectedStructureRevision(), revision);
        List<CategoryData> tree = categories.findAll();
        validateConfiguredDepth(tree);
        if (input.parentId() != null) require(tree, input.parentId());
        List<CategoryData> siblings = siblings(tree, input.parentId());
        List<UUID> currentIds = siblings.stream().map(CategoryData::id).toList();
        if (input.orderedIds() == null
                || input.orderedIds().size() != currentIds.size()
                || new HashSet<>(input.orderedIds()).size()
                        != input.orderedIds().size()
                || !new HashSet<>(currentIds).equals(new HashSet<>(input.orderedIds()))) throw invalid("orderedIds");
        if (currentIds.equals(input.orderedIds())) return new StructureResult(revision, null);
        if (!advanceRevision(revision)) throw structureConflict();
        Map<UUID, CategoryData> byId = new HashMap<>();
        siblings.forEach(c -> byId.put(c.id(), c));
        Instant now = now();
        for (int index = 0; index < input.orderedIds().size(); index++)
            categories.saveAndFlush(compact(byId.get(input.orderedIds().get(index)), index, actor, now));
        categories.flush();
        return new StructureResult(revision + 1, null);
    }

    @Transactional
    public void delete(UUID id, long expectedVersion, long expectedRevision, UUID actor) {
        long revision = currentRevision();
        checkRevision(expectedRevision, revision);
        List<CategoryData> tree = categories.findAll();
        validateConfiguredDepth(tree);
        CategoryData target = require(tree, id);
        checkVersion(target, expectedVersion);
        if (tree.stream().anyMatch(c -> Objects.equals(c.parentId(), id)))
            throw new CategoryException(CategoryException.Kind.CATEGORY_IN_USE, "Category has child categories.");
        if (!advanceRevision(revision)) throw structureConflict();
        categories.delete(target);
        Instant now = now();
        for (CategoryData sibling : siblings(tree, target.parentId()))
            if (sibling.displayOrder() > target.displayOrder())
                categories.saveAndFlush(compact(sibling, sibling.displayOrder() - 1, actor, now));
        categories.flush();
    }

    private CategoryData compact(CategoryData entity, int order, UUID actor, Instant now) {
        return entity.move(entity.parentId(), order, now, actor);
    }

    private long currentRevision() {
        return categories.currentStructureRevision();
    }

    private Instant now() {
        return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private boolean advanceRevision(long revision) {
        return categories.compareAndAdvanceStructureRevision(revision);
    }

    private CategoryData find(UUID id) {
        return categories.findById(id).orElseThrow(() -> notFound());
    }

    private CategoryData require(List<CategoryData> all, UUID id) {
        return all.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> notFound());
    }

    private Set<UUID> descendants(List<CategoryData> all, UUID root) {
        Set<UUID> ids = new HashSet<>();
        ids.add(root);
        boolean changed;
        do {
            changed = false;
            for (CategoryData c : all) if (ids.contains(c.parentId())) changed |= ids.add(c.id());
        } while (changed);
        return ids;
    }

    private Set<UUID> subtreeIds(List<CategoryData> all, UUID root) {
        require(all, root);
        return descendants(all, root);
    }

    private int depthOf(List<CategoryData> all, CategoryData c) {
        int depth = 1;
        UUID parent = c.parentId();
        Set<UUID> seen = new HashSet<>();
        seen.add(c.id());
        while (parent != null) {
            if (!seen.add(parent)) throw new IllegalStateException("Stored category cycle.");
            CategoryData p = require(all, parent);
            depth++;
            parent = p.parentId();
        }
        ensureDepth(depth);
        return depth;
    }

    private void validateConfiguredDepth(List<CategoryData> all) {
        for (CategoryData category : all) depthOf(all, category);
    }

    private void verifyConfiguredDepth() {
        if (!categories.hierarchyWithinDepth(limits.maxDepth())) {
            throw new IllegalStateException("Configured maximum depth is below the current category tree depth.");
        }
    }

    private int subtreeHeight(List<CategoryData> all, UUID root) {
        int rootDepth = depthOf(all, require(all, root));
        return descendants(all, root).stream()
                .map(id -> depthOf(all, require(all, id)) - rootDepth + 1)
                .max(Integer::compare)
                .orElse(1);
    }

    private List<CategoryData> siblings(List<CategoryData> all, UUID parent) {
        return new ArrayList<>(all.stream()
                .filter(c -> Objects.equals(c.parentId(), parent))
                .sorted(Comparator.comparingInt(CategoryData::displayOrder))
                .toList());
    }

    private TreeNode treeNode(CategoryData c, List<CategoryData> all, long revision) {
        int depth = depthOf(all, c);
        boolean effective = c.status().equals("ACTIVE") && c.visibility().equals("PUBLIC");
        CategoryData parent = c.parentId() == null ? null : require(all, c.parentId());
        while (parent != null) {
            effective &= parent.status().equals("ACTIVE") && parent.visibility().equals("PUBLIC");
            parent = parent.parentId() == null ? null : require(all, parent.parentId());
        }
        return new TreeNode(view(c, revision, effective), depth, effective);
    }

    private void appendTree(List<CategoryData> all, UUID parent, List<TreeNode> result, long revision) {
        for (CategoryData category : siblings(all, parent)) {
            result.add(treeNode(category, all, revision));
            appendTree(all, category.id(), result, revision);
        }
    }

    private CategoryView view(CategoryData c, long revision) {
        return view(c, revision, effectiveVisibility(c.id()));
    }

    private CategoryView view(CategoryData c, long revision, boolean effectivelyVisible) {
        return new CategoryView(
                c.id(),
                c.parentId(),
                c.name(),
                c.slug(),
                c.description(),
                c.imageAssetId(),
                c.status(),
                c.visibility(),
                c.displayOrder(),
                c.version() == null ? 0 : c.version(),
                c.createdAt(),
                c.createdBy(),
                c.updatedAt(),
                c.updatedBy(),
                revision,
                effectivelyVisible);
    }

    private boolean effectiveVisibility(UUID id) {
        return categories.effectiveVisibilityFor(List.of(id)).getOrDefault(id, false);
    }

    private String cleanName(String name) {
        if (name == null
                || name.trim().isEmpty()
                || name.trim().codePointCount(0, name.trim().length()) > 200) throw invalid("name");
        return name.trim();
    }

    private String cleanDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.codePointCount(0, text.length()) > 4000) throw invalid("description");
        return text;
    }

    private String normalizeSlug(String slug, String name) {
        String result = slug == null || slug.isBlank() ? CategorySlugPolicy.suggest(name) : slug.trim();
        if (!CategorySlugPolicy.valid(result)) throw invalid("slug");
        return result;
    }

    private String status(String value, String fallback) {
        String result = value == null ? fallback : value;
        if (!Set.of("ACTIVE", "INACTIVE").contains(result)) throw invalid("status");
        return result;
    }

    private String visibility(String value, String fallback) {
        String result = value == null ? fallback : value;
        if (!Set.of("PUBLIC", "PRIVATE").contains(result)) throw invalid("visibility");
        return result;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("page/size");
    }

    private void ensureDepth(int depth) {
        if (depth > limits.maxDepth())
            throw new CategoryException(
                    CategoryException.Kind.CATEGORY_DEPTH_EXCEEDED,
                    "The category exceeds the configured maximum depth.");
    }

    private void checkVersion(CategoryData entity, long expected) {
        if (entity.version() != null && entity.version() != expected)
            throw new CategoryException(CategoryException.Kind.CATEGORY_VERSION_CONFLICT, "The category has changed.");
    }

    private void checkRevision(long expected, long current) {
        if (expected != current) throw structureConflict();
    }

    private CategoryException notFound() {
        return new CategoryException(CategoryException.Kind.CATEGORY_NOT_FOUND, "Category was not found.");
    }

    private CategoryException structureConflict() {
        return new CategoryException(
                CategoryException.Kind.CATEGORY_STRUCTURE_CONFLICT,
                "The category structure has changed; refetch it and retry.");
    }

    private CategoryException invalid(String field) {
        return new CategoryException(
                CategoryException.Kind.BAD_REQUEST, "The supplied category fields are invalid: " + field + ".");
    }

    public record Create(
            String name,
            String slug,
            String description,
            UUID imageAssetId,
            UUID parentId,
            String status,
            String visibility) {}

    public record Update(
            String name,
            String slug,
            String description,
            String imageAction,
            UUID imageAssetId,
            String status,
            String visibility,
            long expectedVersion) {}

    public record Move(UUID parentId, long expectedVersion, long expectedStructureRevision) {}

    public record Reorder(UUID parentId, List<UUID> orderedIds, long expectedStructureRevision) {}

    public record Query(
            int page,
            int size,
            String q,
            String status,
            String visibility,
            UUID parentId,
            boolean rootsOnly,
            UUID rootId) {}

    public record Snapshot(long structureRevision, List<TreeNode> categories) {}

    public record TreeNode(CategoryView category, int depth, boolean effectivelyVisible) {}

    public record StructureResult(long structureRevision, CategoryView category) {}
}
