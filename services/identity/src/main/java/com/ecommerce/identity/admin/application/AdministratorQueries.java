package com.ecommerce.identity.admin.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ecommerce.identity.admin.domain.Permission;

public interface AdministratorQueries {
    Page list(int page, int size);

    AdministratorView find(UUID id);

    List<String> roles();

    List<Permission> permissions();

    public record AdministratorView(UUID id, String username, boolean enabled, Set<String> roleCodes) {}

    public record Page(List<AdministratorView> items, int page, int size, long total) {}
}
