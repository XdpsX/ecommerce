package com.ecommerce.identity;

import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IdentityApplication.class);
        String[] effectiveArgs = args;
        if (Arrays.stream(args).anyMatch(argument -> argument.startsWith("--identity.command="))) {
            application.setWebApplicationType(WebApplicationType.NONE);
            var commandArgs = new ArrayList<>(Arrays.asList(args));
            commandArgs.removeIf(argument -> argument.startsWith("--identity.server-enabled="));
            commandArgs.add("--identity.server-enabled=false");
            effectiveArgs = commandArgs.toArray(String[]::new);
        }
        application.run(effectiveArgs);
    }
}
