package com.ecommerce.identity.admin.application;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.exception.AdministratorException;

@Service
public class BootstrapAdministratorService {
    private final BootstrapAdministratorStore store;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdministratorService(BootstrapAdministratorStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean bootstrap(String usernameInput, char[] password) {
        try {
            return doBootstrap(usernameInput, password);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private boolean doBootstrap(String usernameInput, char[] password) {
        String username = normalizeUsername(usernameInput);
        validatePassword(password);
        BootstrapAdministratorStore.Marker marker = store.lockMarker();
        if (marker.completed()) {
            return false;
        }
        if (marker.administratorId() != null || store.administratorCount() != 0) {
            throw new AdministratorException("Administrators exist without a completed bootstrap marker.");
        }
        UUID administratorId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(CharBuffer.wrap(password));
        store.create(administratorId, username, passwordHash);
        return true;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank() || username.trim().length() > 190) {
            throw new AdministratorException("Username must contain between 1 and 190 characters.");
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validatePassword(char[] password) {
        if (password == null || password.length < 12) {
            throw new AdministratorException("Password must contain at least 12 characters.");
        }
        var encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        if (encoded.remaining() > 72) {
            throw new AdministratorException("Password must contain at most 72 UTF-8 bytes.");
        }
    }
}
