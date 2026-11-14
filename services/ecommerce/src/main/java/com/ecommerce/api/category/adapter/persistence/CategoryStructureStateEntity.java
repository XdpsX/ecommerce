package com.ecommerce.api.category.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category_structure_state")
public class CategoryStructureStateEntity {
    @Id
    @Column(name = "singleton_id")
    Short singletonId;

    @Column(nullable = false)
    Long revision;

    protected CategoryStructureStateEntity() {}
}
