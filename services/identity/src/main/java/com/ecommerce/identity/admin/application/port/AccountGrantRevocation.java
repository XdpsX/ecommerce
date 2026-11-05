package com.ecommerce.identity.admin.application.port;

import java.util.UUID;

public interface AccountGrantRevocation {
    void revoke(UUID administratorId, String username);
}
