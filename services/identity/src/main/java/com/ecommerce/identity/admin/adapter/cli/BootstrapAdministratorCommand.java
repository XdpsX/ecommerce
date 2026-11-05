package com.ecommerce.identity.admin.adapter.cli;

import java.io.Console;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ecommerce.identity.admin.application.exception.AdministratorException;
import com.ecommerce.identity.admin.application.service.BootstrapAdministratorService;

@Component
@ConditionalOnProperty(name = "identity.command", havingValue = "bootstrap-admin")
public class BootstrapAdministratorCommand implements ApplicationRunner {
    private static final System.Logger LOGGER = System.getLogger(BootstrapAdministratorCommand.class.getName());

    private final BootstrapAdministratorService service;

    public BootstrapAdministratorCommand(BootstrapAdministratorService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        Console console = System.console();
        if (console == null) {
            throw new AdministratorException("Bootstrap requires an interactive terminal console.");
        }
        String username = console.readLine("Initial administrator username: ");
        char[] password = console.readPassword("Initial administrator password: ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new AdministratorException("Passwords do not match.");
            }
            boolean created = service.bootstrap(username, password);
            console.printf(
                    created
                            ? "Initial administrator created.%n"
                            : "Bootstrap was already completed; no changes made.%n");
            LOGGER.log(
                    System.Logger.Level.INFO,
                    created ? "Initial administrator bootstrap completed." : "Bootstrap already completed.");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
