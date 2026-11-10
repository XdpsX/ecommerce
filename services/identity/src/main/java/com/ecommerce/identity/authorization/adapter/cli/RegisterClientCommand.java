package com.ecommerce.identity.authorization.adapter.cli;

import java.io.Console;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ecommerce.identity.authorization.application.port.ClientRegistration;
import com.ecommerce.identity.config.IdentityProperties;

@Component
@ConditionalOnProperty(name = "identity.command", havingValue = "register-client")
public class RegisterClientCommand implements ApplicationRunner {
    private final ClientRegistration registrationService;
    private final IdentityProperties properties;

    public RegisterClientCommand(ClientRegistration registrationService, IdentityProperties properties) {
        this.registrationService = registrationService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Client registration requires an interactive terminal console.");
        }
        char[] secret = console.readPassword(
                "%s client secret: ", properties.getClient().getClientName());
        char[] confirmation = console.readPassword("Confirm client secret: ");
        try {
            if (secret == null || confirmation == null || !Arrays.equals(secret, confirmation)) {
                throw new IllegalArgumentException("Client secrets do not match.");
            }
            boolean created = registrationService.register(secret);
            console.printf(
                    created
                            ? "Confidential client registered.%n"
                            : "Client already matches configuration; no changes made.%n");
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
