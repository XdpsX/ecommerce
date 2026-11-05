package com.ecommerce.identity.authorization.adapter.cli;

import java.io.Console;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ecommerce.identity.authorization.application.port.ClientRegistration;

@Component
@ConditionalOnProperty(name = "identity.command", havingValue = "add-media-scope")
public class AddMediaScopeCommand implements ApplicationRunner {
    private final ClientRegistration registration;

    public AddMediaScopeCommand(ClientRegistration registration) {
        this.registration = registration;
    }

    @Override
    public void run(ApplicationArguments args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Adding a Media scope requires an interactive terminal console.");
        }
        String clientId = args.containsOption("client-id")
                        && !args.getOptionValues("client-id").isEmpty()
                ? args.getOptionValues("client-id").getFirst()
                : console.readLine("Existing confidential client ID: ");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("An explicit OAuth client ID is required.");
        }
        if (!console.readLine("Add media.api to client '%s'? [y/N] ", clientId).equalsIgnoreCase("y")) {
            console.printf("No changes made.%n");
            return;
        }
        boolean changed = registration.addMediaScope(clientId);
        console.printf(
                changed ? "Media scope added; existing grants are unchanged.%n" : "Media scope already present.%n");
    }
}
