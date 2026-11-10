package com.ecommerce.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.backoffice.shared.security.BrowserBoundaryFilter;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
class BffFlowIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.8").withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static Process identity;
    static ConfigurableApplicationContext bff;
    static HttpServer downstream;
    static String issuer, base;
    static int bffPort;
    static Path keys, identityLog;
    static final String SECRET = "bff-integration-test-client-secret-123";
    static final String USER = "bff.test", PASSWORD = "Bff-password-test-123!";
    static java.util.concurrent.atomic.AtomicReference<Map<String, String>> received =
            new java.util.concurrent.atomic.AtomicReference<>();
    static HttpClient browser;
    static CookieManager cookies;
    static String[] settings;
    static final java.util.concurrent.atomic.AtomicReference<Instant> testNow =
            new java.util.concurrent.atomic.AtomicReference<>();

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TimeFixture {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock controlledClock() {
            return new Clock() {
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                public Clock withZone(ZoneId zone) {
                    return this;
                }

                public Instant instant() {
                    return testNow.get() == null ? Instant.now() : testNow.get();
                }
            };
        }
    }

    @BeforeAll
    static void start() throws Exception {
        bffPort = Integer.getInteger("bff.fixture.port", port());
        base = "http://localhost:" + bffPort;
        issuer = "http://localhost:" + port();
        var key = new RSAKeyGenerator(2048)
                .keyID("bff-it-key")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        keys = Files.createTempFile("bff-it-key", ".json");
        Files.writeString(keys, new JWKSet(key).toString(false));
        identityLog = Path.of("target", "identity-it.log");
        var process = new ProcessBuilder(
                "java",
                "-Duser.timezone=UTC",
                "-jar",
                Path.of("../identity/target/identity-0.0.1-SNAPSHOT.jar")
                        .toAbsolutePath()
                        .toString());
        process.redirectErrorStream(true).redirectOutput(identityLog.toFile());
        var env = process.environment();
        env.put("IDENTITY_DB_URL", POSTGRES.getJdbcUrl());
        env.put("IDENTITY_DB_USERNAME", POSTGRES.getUsername());
        env.put("IDENTITY_DB_PASSWORD", POSTGRES.getPassword());
        env.put("IDENTITY_PORT", String.valueOf(URI.create(issuer).getPort()));
        env.put("IDENTITY_ISSUER", issuer);
        env.put("IDENTITY_JWK_SET_PATH", keys.toString());
        env.put("IDENTITY_ACTIVE_KEY_ID", "bff-it-key");
        identity = process.start();
        var http = HttpClient.newHttpClient();
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (true) {
            try {
                if (http.send(
                                        HttpRequest.newBuilder(URI.create(issuer + "/.well-known/openid-configuration"))
                                                .timeout(Duration.ofSeconds(2))
                                                .GET()
                                                .build(),
                                        HttpResponse.BodyHandlers.discarding())
                                .statusCode()
                        == 200) break;
            } catch (Exception ignored) {
            }
            if (!identity.isAlive() || System.nanoTime() > deadline)
                throw new IllegalStateException("Identity fixture unavailable; see " + identityLog);
            Thread.sleep(200);
        }
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (var connection = ds.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO administrators(id,username,password_hash,enabled) VALUES (?,?,?,true)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, USER);
            statement.setString(
                    3,
                    PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD));
            statement.executeUpdate();
        }
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        UUID administrator = jdbc.queryForObject("SELECT id FROM administrators WHERE username = ?", UUID.class, USER);
        UUID role = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'SUPER_ADMIN'", UUID.class);
        UUID permission = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO permissions(id,code,audience) VALUES (?, 'bff-test:read', 'ecommerce-api')", permission);
        jdbc.update("INSERT INTO administrator_roles(administrator_id,role_id) VALUES (?,?)", administrator, role);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", role, permission);
        new JdbcRegisteredClientRepository(new org.springframework.jdbc.core.JdbcTemplate(ds))
                .save(RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("backoffice-bff")
                        .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                                .encode(SECRET))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000/login/oauth2/code/identity")
                        .scope("openid")
                        .scope("profile")
                        .scope("ecommerce.api")
                        .clientSettings(ClientSettings.builder()
                                .requireProofKey(true)
                                .requireAuthorizationConsent(false)
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                        .build());
        downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> jwt.getAudience().contains("ecommerce-api")
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        downstream.createContext("/api/probe", exchange -> {
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, String.join(",", values)));
            received.set(headers);
            int status;
            try {
                var jwt = decoder.decode(headers.get("Authorization").substring(7));
                String required = exchange.getRequestURI().getQuery() == null ? "bff-test:read" : "bff-test:write";
                status = jwt.getClaimAsStringList("permissions").contains(required) ? 200 : 403;
            } catch (Exception error) {
                status = 401;
            }
            byte[] body = (status == 200
                            ? "{\"ok\":true}"
                            : "{\"type\":\"urn:ecommerce:problem:forbidden\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Denied\",\"code\":\"FORBIDDEN\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .add("Content-Type", status == 200 ? "application/json" : "application/problem+json");
            exchange.getResponseHeaders().add("Set-Cookie", "downstream=must-not-relay");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        downstream.start();
        settings = new String[] {
            "--server.port=" + bffPort,
            "--logging.level.org.springframework=INFO",
            "--debug=false",
            "--spring.main.web-application-type=reactive",
            "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "--spring.data.redis.host=" + REDIS.getHost(),
            "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
            "--spring.data.redis.password=",
            "--spring.security.oauth2.client.provider.identity.issuer-uri=" + issuer,
            "--spring.security.oauth2.client.registration.identity.client-secret=" + SECRET,
            "--spring.security.oauth2.client.registration.identity.redirect-uri=http://localhost:3000/login/oauth2/code/identity",
            "--bff.public-origin=http://localhost:3000",
            "--bff.secure-cookie=false",
            "--bff.ecommerce-uri=http://localhost:" + downstream.getAddress().getPort()
        };
        restart();
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        browser = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    static void restart() {
        if (bff != null) bff.close();
        bff = new SpringApplicationBuilder(BackofficeBffApplication.class, TimeFixture.class).run(settings);
    }

    @AfterAll
    static void stop() throws Exception {
        if (bff != null) bff.close();
        if (downstream != null) downstream.stop(0);
        if (identity != null) {
            identity.destroy();
            if (!identity.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) identity.destroyForcibly();
        }
        if (keys != null) Files.deleteIfExists(keys);
    }

    @Test
    void realLoginRedisRestartRelayLogoutAndExpiry() throws Exception {
        assertThat(get("/bff/session").statusCode()).isEqualTo(401);
        assertThat(post("/bff/logout", null).statusCode()).isEqualTo(403);
        login("/a/..//evil.example");
        assertThat(bff.getBeansOfType(org.springframework.session.data.redis.ReactiveRedisSessionRepository.class))
                .hasSize(1);
        var session = get("/bff/session");
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains("subject").doesNotContain("access_token", "refresh_token", SECRET);
        assertThat(session.headers().firstValue("Cache-Control")).contains("no-store");
        String sessionId = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("BACKOFFICE_SESSION"))
                .findFirst()
                .orElseThrow()
                .getValue();
        var relay = browser.send(
                HttpRequest.newBuilder(URI.create(base + "/api/ecommerce/probe"))
                        .header("Authorization", "Bearer forged")
                        .header("X-User-Id", "forged")
                        .header("X-CSRF-TOKEN", "must-not-relay")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(relay.statusCode()).isEqualTo(200);
        assertThat(received.get().get("Authorization")).startsWith("Bearer ").doesNotContain("forged");
        assertThat(received.get()).doesNotContainKeys("Cookie", "X-CSRF-TOKEN", "X-User-Id");
        assertThat(relay.headers().allValues("Set-Cookie")).noneMatch(value -> value.contains("downstream="));
        assertThat(get("/api/ecommerce/probe?forbidden").statusCode()).isEqualTo(403);
        assertThat(get("/bff/session").statusCode()).isEqualTo(200);
        restart();
        assertThat(get("/bff/session").statusCode()).isEqualTo(200);
        assertThat(get("/api/ecommerce/probe").statusCode()).isEqualTo(200);
        assertThat(post("/bff/logout", "invalid").statusCode()).isEqualTo(403);
        String csrf = field(get("/bff/csrf").body(), "token");
        assertThat(post("/bff/logout", csrf).statusCode()).isEqualTo(204);
        assertThat(get("/bff/session").statusCode()).isEqualTo(401);
        // Replay the actual pre-logout cookie independently of the browser's cookie deletion.
        var replay = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(base + "/bff/session"))
                                .header("Cookie", "BACKOFFICE_SESSION=" + sessionId)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
        login("/");
        login("/orders?state=pending%20review#section%201", "/orders?state=pending%20review#section%201");
        assertThat(get("/api/ecommerce/probe").statusCode()).isEqualTo(200);
        Instant expires = com.nimbusds.jwt.JWTParser.parse(
                        received.get().get("Authorization").substring(7))
                .getJWTClaimsSet()
                .getExpirationTime()
                .toInstant();
        String currentSessionId = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("BACKOFFICE_SESSION"))
                .findFirst()
                .orElseThrow()
                .getValue();
        var stored = (org.springframework.session.Session)
                bff.getBean(org.springframework.session.ReactiveSessionRepository.class)
                        .findById(currentSessionId)
                        .block();
        assertThat(stored).isNotNull();
        org.springframework.security.oauth2.client.OAuth2AuthorizedClient storedClient = null;
        for (String attributeName : stored.getAttributeNames()) {
            Object attribute = stored.getAttribute(attributeName);
            if (attribute instanceof Map<?, ?> values) {
                for (Object value : values.values()) {
                    if (value instanceof org.springframework.security.oauth2.client.OAuth2AuthorizedClient client)
                        storedClient = client;
                }
            }
        }
        assertThat(storedClient).isNotNull();
        assertThat(storedClient.getRefreshToken()).isNull();
        // Both response metadata and JWT exp constrain usability. Derive the
        // actual persisted bound rather than assuming their rounding is equal.
        if (storedClient.getAccessToken().getExpiresAt().isBefore(expires))
            expires = storedClient.getAccessToken().getExpiresAt();
        testNow.set(expires.minusNanos(1));
        assertThat(get("/bff/session").statusCode()).isEqualTo(200);
        testNow.set(expires);
        assertThat(get("/bff/session").statusCode()).isEqualTo(401);
        assertThat(get("/api/ecommerce/probe").statusCode()).isEqualTo(401);
        testNow.set(null);
        login("/");
        String[] healthySettings = settings;
        try {
            settings = withSetting(settings, "--bff.ecommerce-uri=", "http://localhost:" + port());
            restart();
            var unavailableBackend = get("/api/ecommerce/probe");
            assertThat(unavailableBackend.statusCode()).isEqualTo(502);
            assertThat(unavailableBackend.body()).contains("BAD_GATEWAY").doesNotContain("Exception", "localhost");
            assertThat(get("/bff/session").statusCode()).isEqualTo(200);

            settings = withSetting(settings, "--spring.data.redis.port=", String.valueOf(port()));
            restart();
            var unavailable = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create(base + "/bff/session"))
                                    .header("Cookie", "BACKOFFICE_SESSION=" + sessionId)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(unavailable.statusCode()).isEqualTo(503);
        } finally {
            settings = healthySettings;
            restart();
        }
        URI localeAfterOutage = authorizationLocation("/", "vi");
        assertThat(query(localeAfterOutage.getRawQuery()).get("ui_locales")).isEqualTo("vi");
    }

    @Test
    void returnPathsCannotEscapeOrLoop() {
        assertThat(BrowserBoundaryFilter.safeReturn("/a/..//evil.example")).isEqualTo("/");
        assertThat(BrowserBoundaryFilter.safeReturn("//evil.example")).isEqualTo("/");
        assertThat(BrowserBoundaryFilter.safeReturn("/oauth2/authorization/identity"))
                .isEqualTo("/");
        assertThat(BrowserBoundaryFilter.safeReturn("/orders?state=pending")).isEqualTo("/orders?state=pending");
        assertThat(BrowserBoundaryFilter.safeReturn("/orders?state=pending%20review#section%201"))
                .isEqualTo("/orders?state=pending%20review#section%201");
    }

    @Test
    void forwardsOnlyTheFirstSupportedLocaleWithoutLosingPkceParameters() throws Exception {
        URI vietnamese = authorizationLocation("/orders", "fr-FR vi-VN en-US");
        Map<String, String> parameters = query(vietnamese.getRawQuery());

        assertThat(parameters.get("ui_locales")).isEqualTo("vi");
        assertThat(parameters).containsKeys("state", "nonce", "code_challenge");
        assertThat(parameters.get("code_challenge_method")).isEqualTo("S256");
        assertThat(java.net.URLDecoder.decode(vietnamese.toString(), StandardCharsets.UTF_8))
                .contains("http://localhost:3000/login/oauth2/code/identity");

        URI unsupported = authorizationLocation("/", "fr-FR xx-ZZ");
        assertThat(query(unsupported.getRawQuery())).doesNotContainKey("ui_locales");
    }

    static URI authorizationLocation(String target, String locales) throws Exception {
        String url = base + "/oauth2/authorization/identity?returnTo=" + encode(target);
        if (locales != null) url += "&ui_locales=" + encode(locales);
        var response = send(HttpRequest.newBuilder(URI.create(url)).GET().build());
        assertThat(response.statusCode()).isEqualTo(302);
        return URI.create(response.headers().firstValue("Location").orElseThrow());
    }

    static Map<String, String> query(String rawQuery) {
        var result = new LinkedHashMap<String, String>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1 ? "" : java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    static void login(String target) throws Exception {
        login(target, "/");
    }

    static void login(String target, String expectedReturnPath) throws Exception {
        var start = send(
                HttpRequest.newBuilder(URI.create(base + "/oauth2/authorization/identity?returnTo=" + encode(target)))
                        .header("X-Forwarded-Host", "evil.example")
                        .header("Forwarded", "host=evil.example;proto=https")
                        .GET()
                        .build());
        assertThat(start.statusCode()).isEqualTo(302);
        String authorize = start.headers().firstValue("Location").orElseThrow();
        assertThat(authorize).contains("code_challenge=", "code_challenge_method=S256");
        assertThat(java.net.URLDecoder.decode(authorize, StandardCharsets.UTF_8))
                .contains("http://localhost:3000/login/oauth2/code/identity");
        var auth = send(HttpRequest.newBuilder(URI.create(authorize)).GET().build());
        if (auth.headers().firstValue("Location").orElse("").contains("/login")) {
            var page = send(
                    HttpRequest.newBuilder(URI.create(issuer + "/login")).GET().build());
            var matcher =
                    Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
            assertThat(matcher.find()).isTrue();
            send(HttpRequest.newBuilder(URI.create(issuer + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + encode(USER) + "&password="
                            + encode(PASSWORD) + "&_csrf=" + encode(matcher.group(1))))
                    .build());
            auth = send(HttpRequest.newBuilder(URI.create(authorize)).GET().build());
        }
        String callback = auth.headers().firstValue("Location").orElseThrow();
        assertThat(callback).contains("code=");
        var result = send(HttpRequest.newBuilder(URI.create(callback.replace("http://localhost:3000", base)))
                .GET()
                .build());
        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.headers().firstValue("Location")).contains("http://localhost:3000" + expectedReturnPath);
    }

    static HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET().build());
    }

    static HttpResponse<String> post(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return send(request.build());
    }

    static HttpResponse<String> send(HttpRequest request) throws Exception {
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static String field(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String[] withSetting(String[] current, String prefix, String value) {
        String[] updated = current.clone();
        for (int index = 0; index < updated.length; index++) {
            if (updated[index].startsWith(prefix)) {
                updated[index] = prefix + value;
                return updated;
            }
        }
        throw new IllegalArgumentException("Missing test setting: " + prefix);
    }

    static int port() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // Optional local browser verification entry point. E2E scripts stay outside
    // tracked application sources; this reuses the disposable integration fixture.
    public static void main(String[] args) throws Exception {
        System.setProperty("bff.fixture.port", "8081");
        REDIS.start();
        POSTGRES.start();
        try {
            start();
            var process =
                    new ProcessBuilder("node", Path.of(args[0]).toAbsolutePath().toString());
            process.directory(Path.of("../../web/backoffice").toAbsolutePath().toFile());
            process.environment().put("BFF_UPSTREAM", base);
            process.environment().put("BROWSER_TEST_USER", USER);
            process.environment().put("BROWSER_TEST_PASSWORD", PASSWORD);
            process.inheritIO();
            if (process.start().waitFor() != 0) throw new IllegalStateException("Browser verification failed");
        } finally {
            stop();
            REDIS.stop();
            POSTGRES.stop();
        }
    }
}
