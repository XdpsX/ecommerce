package com.ecommerce.api.category.adapter.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataCategoryRepository
        extends JpaRepository<CategoryEntity, UUID>, JpaSpecificationExecutor<CategoryEntity> {}
