package com.ecommerce.api.category.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.api.category.application.model.CategoryData;
import com.ecommerce.api.category.application.model.CategoryFilter;
import com.ecommerce.api.category.application.model.CategoryPageData;

public interface CategoryPersistence {
    List<CategoryData> findAll();

    CategoryPageData findPage(CategoryFilter filter, int page, int size);

    boolean hierarchyWithinDepth(int maximumDepth);

    Map<UUID, Boolean> effectiveVisibilityFor(List<UUID> categoryIds);

    Optional<CategoryData> findById(UUID id);

    CategoryData saveAndFlush(CategoryData category);

    void flush();

    void delete(CategoryData category);

    long currentStructureRevision();

    boolean compareAndAdvanceStructureRevision(long expected);
}
