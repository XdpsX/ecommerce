package com.ecommerce.identity.admin.adapter.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.identity.admin.application.service.AdministratorService;
import com.ecommerce.identity.admin.domain.Administrator;

@RestController
@RequestMapping("/api/admin/administrators")
public class AdministratorManagementWriteController {
    private final AdministratorService administrators;

    public AdministratorManagementWriteController(AdministratorService administrators) {
        this.administrators = administrators;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_administrator:write')")
    public ResponseEntity<CreatedAdministrator> create(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAdministrator body) {
        Administrator created = administrators.create(actor(jwt), body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedAdministrator(created.id(), created.username(), created.enabled()));
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('PERM_administrator:write')")
    public ResponseEntity<Void> setEnabled(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody EnabledRequest body) {
        administrators.setEnabled(actor(jwt), id, body.enabled());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PERM_entitlement:write')")
    public ResponseEntity<Void> replaceRoles(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RolesRequest body) {
        administrators.replaceRoles(actor(jwt), id, body.roleCodes());
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public record CreateAdministrator(
            @NotBlank String username,
            @NotNull @Size(min = 12) String password) {
        @Override
        public String toString() {
            return "CreateAdministrator[credentials=REDACTED]";
        }
    }

    public record CreatedAdministrator(UUID id, String username, boolean enabled) {}

    public record EnabledRequest(@NotNull Boolean enabled) {}

    public record RolesRequest(@NotNull Set<@NotBlank String> roleCodes) {}
}
