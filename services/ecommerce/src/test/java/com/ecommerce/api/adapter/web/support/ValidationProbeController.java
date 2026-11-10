package com.ecommerce.api.adapter.web.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationProbeController {
    @PostMapping("/api/test/validation-probe")
    @PreAuthorize("hasAuthority('PERM_test-probe:read')")
    public ValidationResponse validate(@Valid @RequestBody ValidationRequest request) {
        return new ValidationResponse(request.name());
    }

    public record ValidationRequest(
            @NotBlank String name, @NotBlank @Size(max = 4) String code) {}

    public record ValidationResponse(String name) {}
}
