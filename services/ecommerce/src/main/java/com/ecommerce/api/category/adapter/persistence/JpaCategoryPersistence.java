package com.ecommerce.api.category.adapter.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.ecommerce.api.category.application.exception.CategoryException;
import com.ecommerce.api.category.application.model.CategoryData;
import com.ecommerce.api.category.application.model.CategoryFilter;
import com.ecommerce.api.category.application.model.CategoryPageData;
import com.ecommerce.api.category.application.port.CategoryPersistence;

@Repository
public class JpaCategoryPersistence implements CategoryPersistence {
    private final SpringDataCategoryRepository repository;
    private final EntityManager entityManager;

    public JpaCategoryPersistence(SpringDataCategoryRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public List<CategoryData> findAll() {
        return repository.findAll().stream().map(this::data).toList();
    }

    @Override
    public CategoryPageData findPage(CategoryFilter filter, int page, int size) {
        Set<UUID> subtree = filter.rootId() == null ? null : subtreeIds(filter.rootId());
        Specification<CategoryEntity> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (filter.term() != null && !filter.term().isEmpty()) {
                String escaped = filter.term()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("slug")), pattern, '\\')));
            }
            if (filter.status() != null) predicates.add(builder.equal(root.get("status"), filter.status()));
            if (filter.visibility() != null) predicates.add(builder.equal(root.get("visibility"), filter.visibility()));
            if (filter.parentId() != null) predicates.add(builder.equal(root.get("parentId"), filter.parentId()));
            if (filter.rootsOnly()) predicates.add(builder.isNull(root.get("parentId")));
            if (subtree != null) predicates.add(root.get("id").in(subtree));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = repository.findAll(
                specification, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new CategoryPageData(
                result.getContent().stream().map(this::data).toList(), result.getTotalElements(), page, size);
    }

    @Override
    public boolean hierarchyWithinDepth(int maximumDepth) {
        Number depth = (Number) entityManager
                .createNativeQuery("WITH RECURSIVE tree(id, depth) AS ("
                        + "SELECT id, 1 FROM categories WHERE parent_id IS NULL UNION ALL "
                        + "SELECT c.id, t.depth + 1 FROM categories c JOIN tree t ON c.parent_id = t.id) "
                        + "SELECT COALESCE(MAX(depth), 0) FROM tree")
                .getSingleResult();
        return depth.intValue() <= maximumDepth;
    }

    @Override
    public Map<UUID, Boolean> effectiveVisibilityFor(List<UUID> categoryIds) {
        if (categoryIds.isEmpty()) return Map.of();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery("WITH RECURSIVE visibility_tree(id, parent_id, visible) AS ("
                        + "SELECT id, parent_id, status = 'ACTIVE' AND visibility = 'PUBLIC' "
                        + "FROM categories WHERE parent_id IS NULL UNION ALL "
                        + "SELECT c.id, c.parent_id, t.visible AND c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' "
                        + "FROM categories c JOIN visibility_tree t ON c.parent_id = t.id) "
                        + "SELECT id, visible FROM visibility_tree WHERE id IN (:ids)")
                .setParameter("ids", categoryIds)
                .getResultList();
        Map<UUID, Boolean> result = new HashMap<>();
        for (Object[] row : rows) result.put((UUID) row[0], (Boolean) row[1]);
        return result;
    }

    @Override
    public Optional<CategoryData> findById(UUID id) {
        return repository.findById(id).map(this::data);
    }

    @Override
    public CategoryData saveAndFlush(CategoryData data) {
        CategoryEntity entity;
        if (data.version() == null) {
            entity = CategoryEntity.create(
                    data.id(),
                    data.parentId(),
                    data.name(),
                    data.slug(),
                    data.description(),
                    data.imageAssetId(),
                    data.status(),
                    data.visibility(),
                    data.displayOrder(),
                    data.updatedAt(),
                    data.updatedBy());
        } else {
            entity = repository.findById(data.id()).orElseThrow();
            if (!java.util.Objects.equals(entity.version(), data.version())) {
                throw versionConflict();
            }
            entity.move(data.parentId(), data.displayOrder(), data.updatedAt(), data.updatedBy());
            entity.update(
                    data.name(),
                    data.slug(),
                    data.description(),
                    data.imageAssetId(),
                    data.status(),
                    data.visibility(),
                    data.updatedAt(),
                    data.updatedBy());
        }
        try {
            return data(repository.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw versionConflict();
        }
    }

    @Override
    public void flush() {
        try {
            repository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw versionConflict();
        }
    }

    @Override
    public void delete(CategoryData data) {
        repository.delete(repository.findById(data.id()).orElseThrow());
    }

    @Override
    public long currentStructureRevision() {
        return ((Number) entityManager
                        .createNativeQuery("SELECT revision FROM category_structure_state WHERE singleton_id = 1")
                        .getSingleResult())
                .longValue();
    }

    @Override
    public boolean compareAndAdvanceStructureRevision(long expected) {
        return entityManager
                        .createNativeQuery("UPDATE category_structure_state SET revision = revision + 1 "
                                + "WHERE singleton_id = 1 AND revision = :revision")
                        .setParameter("revision", expected)
                        .executeUpdate()
                == 1;
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> subtreeIds(UUID rootId) {
        List<UUID> values = entityManager
                .createNativeQuery("WITH RECURSIVE subtree(id) AS ("
                        + "SELECT id FROM categories WHERE id = :root UNION "
                        + "SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id) SELECT id FROM subtree")
                .setParameter("root", rootId)
                .getResultList();
        return Set.copyOf(values);
    }

    private CategoryData data(CategoryEntity e) {
        return new CategoryData(
                e.id(),
                e.parentId(),
                e.name(),
                e.slug(),
                e.description(),
                e.imageAssetId(),
                e.status(),
                e.visibility(),
                e.displayOrder(),
                e.version(),
                e.createdAt(),
                e.createdBy(),
                e.updatedAt(),
                e.updatedBy());
    }

    private static CategoryException versionConflict() {
        return new CategoryException(CategoryException.Kind.CATEGORY_VERSION_CONFLICT, "The category has changed.");
    }
}
