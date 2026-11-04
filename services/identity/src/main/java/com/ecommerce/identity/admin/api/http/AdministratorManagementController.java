package com.ecommerce.identity.admin.api.http;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.identity.admin.application.AdministratorQueries;
import com.ecommerce.identity.admin.application.exception.AdministratorNotFoundException;

@RestController
@RequestMapping("/api/admin")
public class AdministratorManagementController {
    private final AdministratorQueries queries;

    public AdministratorManagementController(AdministratorQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/administrators")
    @PreAuthorize("hasAuthority('PERM_administrator:read')")
    public AdministratorQueries.Page administrators(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.list(page, size);
    }

    @GetMapping("/administrators/{id}")
    @PreAuthorize("hasAuthority('PERM_administrator:read')")
    public AdministratorQueries.AdministratorView administrator(@PathVariable UUID id) {
        AdministratorQueries.AdministratorView result = queries.find(id);
        if (result == null) {
            throw new AdministratorNotFoundException();
        }
        return result;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_entitlement:read')")
    public java.util.List<String> roles() {
        return queries.roles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_entitlement:read')")
    public java.util.List<com.ecommerce.identity.admin.domain.Permission> permissions() {
        return queries.permissions();
    }
}
