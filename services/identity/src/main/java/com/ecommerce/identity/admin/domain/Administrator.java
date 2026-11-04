package com.ecommerce.identity.admin.domain;

import java.util.UUID;

public record Administrator(UUID id, String username, boolean enabled) {}
