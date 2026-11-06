package com.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

/** Runs the packaged operator commands against a real terminal, without a web server or signing keys. */
@Testcontainers
class OperatorCommandsIT {
    private static final Network NETWORK = Network.newNetwork();
    private static final String PASSWORD = "operator-smoke-password-123";
    private static final String SECRET = "operator-smoke-client-secret-123456789";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Container
    static final GenericContainer<?> TERMINAL = new GenericContainer<>("eclipse-temurin:25-jre");

    static {
        POSTGRES.withNetwork(NETWORK).withNetworkAliases("database");
        TERMINAL.withNetwork(NETWORK)
                .dependsOn(POSTGRES)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(
                                Path.of("target/identity-0.0.1-SNAPSHOT.jar").toAbsolutePath()),
                        "/identity.jar")
                .withEnv("IDENTITY_DB_URL", "jdbc:postgresql://database:5432/test")
                .withEnv("IDENTITY_DB_USERNAME", "test")
                .withEnv("IDENTITY_DB_PASSWORD", "test")
                .withEnv("IDENTITY_CLIENT_REDIRECT_URIS", "https://client.example.test/callback")
                .withCommand("sleep", "infinity");
    }

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connectDatabase() {
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @AfterAll
    static void closeNetwork() {
        // Close the containers before removing their shared network; extension cleanup is idempotent.
        try {
            TERMINAL.close();
        } finally {
            try {
                POSTGRES.close();
            } finally {
                NETWORK.close();
            }
        }
    }

    @Test
    void bootstrapPromptsConfirmsAndExitsWithoutStartingHttp() throws Exception {
        try (Command command = new Command("bootstrap-admin", true)) {
            bootstrapInput(command, PASSWORD, "different-confirmation");
            command.assertExit(1, "Passwords do not match.");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM administrators", Integer.class))
                .isZero();

        try (Command command = new Command("bootstrap-admin", true)) {
            bootstrapInput(command, PASSWORD, PASSWORD);
            command.assertExit(0, "Initial administrator created.");
        }
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM administrators WHERE username = 'smoke.operator'", String.class);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(PASSWORD, hash))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM administrator_roles", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT bootstrap_completed_at IS NOT NULL FROM identity_security_state", Boolean.class))
                .isTrue();

        try (Command command = new Command("bootstrap-admin", true)) {
            bootstrapInput(command, "replacement-password-123", "replacement-password-123");
            command.assertExit(0, "Bootstrap was already completed; no changes made.");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT password_hash FROM administrators WHERE username = 'smoke.operator'", String.class))
                .isEqualTo(hash);
    }

    @Test
    void clientRegistrationPromptsConfirmsAndExitsWithoutStartingHttp() throws Exception {
        try (Command command = new Command("register-client", true)) {
            clientInput(command, SECRET, "different-confirmation");
            command.assertExit(1, "Client secrets do not match.");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_registered_client", Integer.class))
                .isZero();

        try (Command command = new Command("register-client", true)) {
            clientInput(command, SECRET, SECRET);
            command.assertExit(0, "Confidential client registered.");
        }
        String hash = jdbc.queryForObject(
                "SELECT client_secret FROM oauth2_registered_client WHERE client_id = 'backoffice-bff'", String.class);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(SECRET, hash))
                .isTrue();
        try (Command command = new Command("register-client", true)) {
            clientInput(command, SECRET, SECRET);
            command.assertExit(0, "Client already matches configuration; no changes made.");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT client_secret FROM oauth2_registered_client WHERE client_id = 'backoffice-bff'",
                        String.class))
                .isEqualTo(hash);
    }

    @Test
    void commandsWithoutTerminalFailAndExitRatherThanStartingHttp() throws Exception {
        for (String name : new String[] {"bootstrap-admin", "register-client", "add-media-scope"}) {
            try (Command command = new Command(name, false)) {
                command.assertExit(1, "requires an interactive terminal console");
            }
        }
    }

    @Test
    void addMediaScopePromptsDeclinesAndAuditsConfirmedChange() throws Exception {
        String clientId = "media-scope-smoke";
        RegisteredClient client = RegisteredClient.withId(
                        java.util.UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}media-scope-smoke-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example.test/callback")
                .scope("openid")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        new JdbcRegisteredClientRepository(jdbc).save(client);

        try (Command command = new Command("add-media-scope", true, "--client-id=" + clientId)) {
            command.answer("Add media.api to client '" + clientId + "'? [y/N] ", "n");
            command.assertExit(0, "No changes made.");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT scopes FROM oauth2_registered_client WHERE client_id = ?", String.class, clientId))
                .isEqualTo("openid");

        try (Command command = new Command("add-media-scope", true, "--client-id=" + clientId)) {
            command.answer("Add media.api to client '" + clientId + "'? [y/N] ", "y");
            command.assertExit(0, "Media scope added; existing grants are unchanged.");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT scopes FROM oauth2_registered_client WHERE client_id = ?", String.class, clientId))
                .isEqualTo("media.api,openid");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE target_client_id = ? "
                                + "AND action = 'MEDIA_SCOPE_ADDED_TO_CLIENT' AND outcome = 'APPLIED'",
                        Integer.class,
                        clientId))
                .isEqualTo(1);

        try (Command command = new Command("add-media-scope", true, "--client-id=" + clientId)) {
            command.answer("Add media.api to client '" + clientId + "'? [y/N] ", "y");
            command.assertExit(0, "Media scope already present.");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE target_client_id = ? "
                                + "AND action = 'MEDIA_SCOPE_ADDED_TO_CLIENT' AND outcome = 'APPLIED'",
                        Integer.class,
                        clientId))
                .isEqualTo(1);
    }

    private void bootstrapInput(Command command, String password, String confirmation) throws Exception {
        command.answer("Initial administrator username: ", "smoke.operator");
        command.answer("Initial administrator password: ", password);
        command.answer("Confirm password: ", confirmation);
        command.assertSecretsHidden(password, confirmation);
    }

    private void clientInput(Command command, String secret, String confirmation) throws Exception {
        command.answer("Backoffice BFF client secret: ", secret);
        command.answer("Confirm client secret: ", confirmation);
        command.assertSecretsHidden(secret, confirmation);
    }

    private static final class Command implements AutoCloseable {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Set<String> enteredSecrets = new HashSet<>();
        private final PipedOutputStream keyboard = new PipedOutputStream();
        private final PipedInputStream input = new PipedInputStream(keyboard);
        private final ResultCallback.Adapter<Frame> callback;
        private final String execId;

        Command(String name, boolean terminal, String... options) throws IOException {
            String[] command = new String[6 + options.length];
            command[0] = "java";
            command[1] = "-jar";
            command[2] = "/identity.jar";
            command[3] = "--identity.command=" + name;
            command[4] = "--identity.server-enabled=true";
            command[5] = "--server.port=-1";
            System.arraycopy(options, 0, command, 6, options.length);
            execId = TERMINAL.getDockerClient()
                    .execCreateCmd(TERMINAL.getContainerId())
                    .withAttachStdin(terminal)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(terminal)
                    .withCmd(command)
                    .exec()
                    .getId();
            callback = new ResultCallback.Adapter<>() {
                @Override
                public void onError(Throwable failure) {
                    // Docker Desktop may report terminal EOF as a closed Windows named pipe.
                    // assertExit independently requires the process to terminate with the expected code/output.
                    if (terminal && failure instanceof IOException) super.onComplete();
                    else super.onError(failure);
                }

                @Override
                public void onNext(Frame frame) {
                    synchronized (output) {
                        output.writeBytes(frame.getPayload());
                    }
                }
            };
            var start = TERMINAL.getDockerClient().execStartCmd(execId).withTty(terminal);
            if (terminal) start.withStdIn(input);
            start.exec(callback);
        }

        void answer(String prompt, String value) throws IOException {
            await().atMost(Duration.ofSeconds(45)).until(() -> text().contains(prompt));
            keyboard.write((value + "\n").getBytes(StandardCharsets.UTF_8));
            keyboard.flush();
        }

        void assertExit(long expected, String message) throws InterruptedException {
            assertThat(callback.awaitCompletion(45, TimeUnit.SECONDS))
                    .as("CLI must exit promptly")
                    .isTrue();
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> TERMINAL.getDockerClient()
                                    .inspectExecCmd(execId)
                                    .exec()
                                    .getExitCodeLong()
                            != null);
            assertThat(TERMINAL.getDockerClient().inspectExecCmd(execId).exec().getExitCodeLong())
                    .isEqualTo(expected);
            assertThat(text()).contains(message).doesNotContain("Tomcat", "Jetty", "Netty started");
            assertSecretsHidden(PASSWORD, SECRET);
        }

        void assertSecretsHidden(String... secrets) {
            enteredSecrets.addAll(java.util.List.of(secrets));
            for (String secret : enteredSecrets) assertThat(text()).doesNotContain(secret);
        }

        private String text() {
            synchronized (output) {
                return output.toString(StandardCharsets.UTF_8);
            }
        }

        @Override
        public void close() throws IOException {
            keyboard.close();
            input.close();
            callback.close();
        }
    }
}
