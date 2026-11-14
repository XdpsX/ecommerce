package com.ecommerce.api.category.domain.service;

import java.text.Normalizer;
import java.util.Locale;

public final class CategorySlugPolicy {
    private CategorySlugPolicy() {}

    public static String suggest(String name) {
        String vietnamese = name.replace("Đ", "D").replace("đ", "d");
        String value = Normalizer.normalize(vietnamese, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (value.length() > 200) value = value.substring(0, 200).replaceAll("-$", "");
        return value;
    }

    public static boolean valid(String slug) {
        return slug != null && slug.length() <= 200 && slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }
}
