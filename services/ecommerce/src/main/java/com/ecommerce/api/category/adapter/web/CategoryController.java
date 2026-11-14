package com.ecommerce.api.category.adapter.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.api.category.application.model.CategoryView;
import com.ecommerce.api.category.application.service.CategoryService;
import com.ecommerce.api.category.application.service.CategoryService.Create;
import com.ecommerce.api.category.application.service.CategoryService.Move;
import com.ecommerce.api.category.application.service.CategoryService.Query;
import com.ecommerce.api.category.application.service.CategoryService.Reorder;
import com.ecommerce.api.category.application.service.CategoryService.Update;

@RestController
@Validated
@RequestMapping("/api/admin/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CategoryView> create(@Valid @RequestBody CreateRequest request, JwtAuthenticationToken auth) {
        CategoryView result = service.create(
                new Create(
                        request.name(),
                        request.slug(),
                        request.description(),
                        request.imageAssetId(),
                        request.parentId(),
                        request.status(),
                        request.visibility()),
                actor(auth));
        return ResponseEntity.created(URI.create("/api/admin/categories/" + result.id()))
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @GetMapping
    ResponseEntity<CategoryPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "false") boolean rootsOnly,
            @RequestParam(required = false) UUID rootId) {
        var result = service.list(new Query(page, size, q, status, visibility, parentId, rootsOnly, rootId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CategoryPage(
                        result.getContent(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.getNumber(),
                        result.getSize()));
    }

    @GetMapping("/structure")
    ResponseEntity<CategoryService.Snapshot> structure() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot());
    }

    @GetMapping("/slug-suggestion")
    ResponseEntity<SlugSuggestion> slugSuggestion(@RequestParam @NotBlank @Size(max = 200) String name) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SlugSuggestion(service.suggest(name)));
    }

    @GetMapping("/{id}")
    ResponseEntity<CategoryView> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }

    @PutMapping("/{id}")
    ResponseEntity<CategoryView> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest request, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.update(
                        id,
                        new Update(
                                request.name(),
                                request.slug(),
                                request.description(),
                                request.imageAction(),
                                request.imageAssetId(),
                                request.status(),
                                request.visibility(),
                                request.expectedVersion()),
                        actor(auth)));
    }

    @PostMapping("/{id}/move")
    ResponseEntity<CategoryService.StructureResult> move(
            @PathVariable UUID id, @Valid @RequestBody MoveRequest request, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.move(
                        id,
                        new Move(request.parentId(), request.expectedVersion(), request.expectedStructureRevision()),
                        actor(auth)));
    }

    @PutMapping("/sibling-order")
    ResponseEntity<CategoryService.StructureResult> reorder(
            @Valid @RequestBody ReorderRequest request, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.reorder(
                        new Reorder(request.parentId(), request.orderedIds(), request.expectedStructureRevision()),
                        actor(auth)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam long expectedVersion,
            @RequestParam long expectedStructureRevision,
            JwtAuthenticationToken auth) {
        service.delete(id, expectedVersion, expectedStructureRevision, actor(auth));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static UUID actor(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid actor");
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String slug,

            @Size(max = 4000) String description,

            UUID imageAssetId,
            UUID parentId,
            String status,
            String visibility) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String slug,
            @Size(max = 4000) String description,
            String imageAction,
            UUID imageAssetId,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
            @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE") String visibility,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record MoveRequest(
            UUID parentId,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @PositiveOrZero Long expectedStructureRevision) {}

    public record ReorderRequest(
            UUID parentId,
            @NotNull java.util.List<@NotNull UUID> orderedIds,
            @NotNull @PositiveOrZero Long expectedStructureRevision) {}

    public record SlugSuggestion(String slug) {}

    public record CategoryPage(
            java.util.List<CategoryView> content, long totalElements, int totalPages, int number, int size) {}
}
