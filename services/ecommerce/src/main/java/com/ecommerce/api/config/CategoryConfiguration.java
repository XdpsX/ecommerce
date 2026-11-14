package com.ecommerce.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.ecommerce.api.category.application.model.CategoryLimits;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CategoryProperties.class)
class CategoryConfiguration {
    @org.springframework.context.annotation.Bean
    CategoryLimits categoryLimits(CategoryProperties properties) {
        return new CategoryLimits(properties.maxDepth());
    }
}
