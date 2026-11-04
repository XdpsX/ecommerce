package com.ecommerce.identity.admin.application;

import java.util.UUID;

public interface AccountGrantRevocation {
    void revoke(UUID administratorId, String username);
}
