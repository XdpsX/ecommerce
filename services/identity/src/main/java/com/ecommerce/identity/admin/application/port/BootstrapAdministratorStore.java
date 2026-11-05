package com.ecommerce.identity.admin.application.port;

import java.util.UUID;

public interface BootstrapAdministratorStore {
    Marker lockMarker();

    int administratorCount();

    void create(UUID administratorId, String username, String passwordHash);

    record Marker(boolean completed, UUID administratorId) {}
}
