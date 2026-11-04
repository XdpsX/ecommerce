package com.ecommerce.identity.admin.domain;

import java.util.Set;

public record Entitlements(Set<String> roles, Set<Permission> permissions) {
    public Entitlements {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
