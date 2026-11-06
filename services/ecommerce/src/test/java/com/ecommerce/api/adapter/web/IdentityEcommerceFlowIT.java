package com.ecommerce.api.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.api.adapter.web.support.SecurityProbeConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityProbeConfiguration.class)
class IdentityEcommerceFlowIT {
    private static final String CLIENT_ID = "ecommerce-cross-service-it";
    private static final String CLIENT_SECRET = "cross-service-client-secret-123";
    private static final String REDIRECT_URI = "http://localhost:4188/callback";
    private static final String USERNAME = "cross-service.admin";
    private static final String PASSWORD = "Cross-service-password-123!";
    private static final String VERIFIER = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFG";
    private static PostgreSQLContainer postgres;
    private static PostgreSQLContainer ecommercePostgres;
    private static Process identity;
    private static Path keyFile;
    private static String issuer;
    private static String jdbcUrl;
    private static String dbUsername;
    private static String dbPassword;
    private static UUID testPermissionId;
    private static UUID superAdminRoleId;

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startFixture();
        registry.add("spring.datasource.url", ecommercePostgres::getJdbcUrl);
        registry.add("spring.datasource.username", ecommercePostgres::getUsername);
        registry.add("spring.datasource.password", ecommercePostgres::getPassword);
        registry.add("identity.issuer", () -> issuer);
        registry.add("identity.jwk-set-uri", () -> issuer + "/oauth2/jwks");
        registry.add("identity.audience", () -> "ecommerce-api");
    }

    @AfterAll
    static void stopFixture() throws Exception {
        if (identity != null) {
            identity.destroy();
            if (!identity.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                identity.destroyForcibly();
            }
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (ecommercePostgres != null) {
            ecommercePostgres.stop();
        }
        if (keyFile != null) {
            Files.deleteIfExists(keyFile);
        }
    }

    @Test
    void realIdentityAuthorizationCodeTokenIsValidatedAndPermissionChangesApplyToNewTokens() throws Exception {
        String firstToken = authorizeAndExchange();
        mvc.perform(get("/api/test/security-probe").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?")) {
            statement.setObject(1, superAdminRoleId);
            statement.setObject(2, testPermissionId);
            statement.executeUpdate();
        }

        String nextToken = authorizeAndExchange();
        mvc.perform(get("/api/test/security-probe").header("Authorization", "Bearer " + nextToken))
                .andExpect(status().isForbidden());
    }

    private String authorizeAndExchange() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        String authorizeUrl = issuer + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&scope=" + encode("openid ecommerce.api")
                + "&state=" + encode(UUID.randomUUID().toString())
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
        HttpResponse<String> authorization = send(
                client, HttpRequest.newBuilder(URI.create(authorizeUrl)).GET().build());
        assertThat(authorization.statusCode()).isEqualTo(302);
        HttpResponse<String> loginPage = send(
                client,
                HttpRequest.newBuilder(URI.create(issuer + "/login")).GET().build());
        Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();
        String loginBody =
                "username=" + encode(USERNAME) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(csrf.group(1));
        send(
                client,
                HttpRequest.newBuilder(URI.create(issuer + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                        .build());
        authorization = send(
                client, HttpRequest.newBuilder(URI.create(authorizeUrl)).GET().build());
        URI callback = URI.create(authorization.headers().firstValue("Location").orElseThrow());
        String code = java.util.Arrays.stream(callback.getRawQuery().split("&"))
                .filter(part -> part.startsWith("code="))
                .map(part -> java.net.URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
        String form = "grant_type=authorization_code&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&code_verifier=" + encode(VERIFIER);
        String basic =
                Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> tokens = send(
                client,
                HttpRequest.newBuilder(URI.create(issuer + "/oauth2/token"))
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build());
        assertThat(tokens.statusCode()).isEqualTo(200);
        Matcher accessToken = Pattern.compile("\"access_token\":\"([^\"]+)\"").matcher(tokens.body());
        assertThat(accessToken.find()).isTrue();
        return accessToken.group(1);
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static synchronized void startFixture() {
        if (identity != null) {
            return;
        }
        try {
            postgres = new PostgreSQLContainer("postgres:18.6");
            postgres.start();
            ecommercePostgres = new PostgreSQLContainer("postgres:18.6");
            ecommercePostgres.start();
            jdbcUrl = postgres.getJdbcUrl();
            dbUsername = postgres.getUsername();
            dbPassword = postgres.getPassword();
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            issuer = "http://127.0.0.1:" + port;
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyID("ecommerce-flow-it-key")
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            keyFile = Files.createTempFile("ecommerce-flow-it-key", ".json");
            Files.writeString(keyFile, new JWKSet(key).toString(false));

            Path jar = Path.of("..", "identity", "target", "identity-0.0.1-SNAPSHOT.jar")
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException(
                        "Build the Identity JAR before running Ecommerce integration tests: " + jar);
            }
            ProcessBuilder process = new ProcessBuilder("java", "-jar", jar.toString());
            process.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process.environment().put("IDENTITY_DB_URL", jdbcUrl);
            process.environment().put("IDENTITY_DB_USERNAME", dbUsername);
            process.environment().put("IDENTITY_DB_PASSWORD", dbPassword);
            process.environment().put("IDENTITY_PORT", Integer.toString(port));
            process.environment().put("IDENTITY_ISSUER", issuer);
            process.environment().put("IDENTITY_JWK_SET_PATH", keyFile.toString());
            process.environment().put("IDENTITY_ACTIVE_KEY_ID", "ecommerce-flow-it-key");
            process.environment().put("IDENTITY_CLIENT_REDIRECT_URIS", REDIRECT_URI);
            identity = process.start();
            awaitIdentity();
            provisionIdentity();
        } catch (Exception exception) {
            if (identity != null) {
                identity.destroyForcibly();
                identity = null;
            }
            if (postgres != null) {
                postgres.stop();
                postgres = null;
            }
            if (ecommercePostgres != null) {
                ecommercePostgres.stop();
                ecommercePostgres = null;
            }
            if (keyFile != null) {
                try {
                    Files.deleteIfExists(keyFile);
                } catch (Exception ignored) {
                    // Preserve the fixture startup error.
                }
                keyFile = null;
            }
            throw new IllegalStateException("Could not start the Identity to Ecommerce test fixture.", exception);
        }
    }

    private static void awaitIdentity() throws Exception {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            if (!identity.isAlive()) {
                throw new IllegalStateException("The Identity process exited before becoming ready.");
            }
            try {
                HttpResponse<Void> response = client.send(
                        HttpRequest.newBuilder(URI.create(issuer + "/.well-known/openid-configuration"))
                                .timeout(Duration.ofSeconds(2))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("Identity did not become ready within 90 seconds.");
    }

    private static void provisionIdentity() throws Exception {
        UUID administratorId = UUID.randomUUID();
        testPermissionId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword)) {
            connection.setAutoCommit(false);
            try (PreparedStatement admin = connection.prepareStatement(
                            "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)");
                    PreparedStatement findRole =
                            connection.prepareStatement("SELECT id FROM roles WHERE code = 'SUPER_ADMIN'");
                    PreparedStatement permission = connection.prepareStatement(
                            "INSERT INTO permissions(id, code, audience) VALUES (?, 'test-probe:read', 'ecommerce-api')");
                    PreparedStatement roleAssignment = connection.prepareStatement(
                            "INSERT INTO administrator_roles(administrator_id, role_id) VALUES (?, ?)");
                    PreparedStatement rolePermission = connection.prepareStatement(
                            "INSERT INTO role_permissions(role_id, permission_id) VALUES (?, ?)")) {
                admin.setObject(1, administratorId);
                admin.setString(2, USERNAME);
                admin.setString(
                        3,
                        PasswordEncoderFactories.createDelegatingPasswordEncoder()
                                .encode(PASSWORD));
                admin.executeUpdate();
                UUID roleId;
                try (var result = findRole.executeQuery()) {
                    if (!result.next())
                        throw new IllegalStateException("The Identity migration did not seed SUPER_ADMIN.");
                    roleId = result.getObject(1, UUID.class);
                }
                superAdminRoleId = roleId;
                permission.setObject(1, testPermissionId);
                permission.executeUpdate();
                roleAssignment.setObject(1, administratorId);
                roleAssignment.setObject(2, roleId);
                roleAssignment.executeUpdate();
                rolePermission.setObject(1, roleId);
                rolePermission.setObject(2, testPermissionId);
                rolePermission.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        registerClientUsingIdentityRepository();
    }

    private static void registerClientUsingIdentityRepository() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, dbUsername, dbPassword);
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .encode(CLIENT_SECRET))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .scope("ecommerce.api")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource)).save(client);
    }
}
