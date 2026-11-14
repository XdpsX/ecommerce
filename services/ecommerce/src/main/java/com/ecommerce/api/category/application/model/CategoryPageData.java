package com.ecommerce.api.category.application.model;

import java.util.List;

public record CategoryPageData(List<CategoryData> content, long totalElements, int page, int size) {}
