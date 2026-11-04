package com.ecommerce.api.security.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityProbeController {
    @GetMapping("/api/test/security-probe")
    @PreAuthorize("hasAuthority('PERM_test-probe:read')")
    public ProbeResponse probe() {
        return new ProbeResponse("authorized");
    }

    public record ProbeResponse(String result) {}
}
