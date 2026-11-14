package com.ecommerce.api.category.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategorySlugPolicyTest {
    @Test
    void suggestsVietnameseFriendlyEditableSlug() {
        assertThat(CategorySlugPolicy.suggest("  Đồ uống & Cà phê  ")).isEqualTo("do-uong-ca-phe");
    }

    @Test
    void validatesOnlyLowercaseUrlSafeSegments() {
        assertThat(CategorySlugPolicy.valid("do-uong-1")).isTrue();
        assertThat(CategorySlugPolicy.valid("Do-Uong")).isFalse();
        assertThat(CategorySlugPolicy.valid("-do-uong")).isFalse();
    }
}
