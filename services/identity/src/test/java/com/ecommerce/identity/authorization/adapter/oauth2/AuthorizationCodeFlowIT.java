package com.ecommerce.identity.authorization.adapter.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecommerce.identity.admin.application.exception.LastAdministratorException;
import com.ecommerce.identity.admin.application.service.AdministratorService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthorizationCodeFlowIT {
    private static final String ISSUER = "http://identity.test";
    private static final String CLIENT_ID = "flow-it-client";
    private static final String CLIENT_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String REDIRECT_URI = "http://localhost:4100/oauth/callback";
    private static final String USERNAME = "flow.admin";
    private static final String PASSWORD = "flow-admin-password";
    private static final String VERIFIER = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFG";
    private static final String KEY_ID = "identity-flow-it-key";
    private static final RSAKey SIGNING_KEY;
    private static final Path JWK_SET_FILE;

    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048)
                    .keyID(KEY_ID)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            JWK_SET_FILE = Files.createTempFile("identity-flow-it-jwks", ".json");
            Files.writeString(JWK_SET_FILE, new JWKSet(java.util.List.of(SIGNING_KEY)).toString(false));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    Environment environment;

    @Autowired
    AdministratorService administratorService;

    private HttpClient http;
    private String baseUrl;
    private UUID administratorId;
    private UUID operatorAdministratorId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("identity.server-enabled", () -> "true");
        registry.add("identity.issuer", () -> ISSUER);
        registry.add("identity.signing-key-set-path", () -> JWK_SET_FILE.toString());
        registry.add("identity.active-signing-key-id", () -> KEY_ID);
        registry.add("identity.backoffice-url", () -> "https://backoffice.test/");
    }

    @BeforeEach
    void setUp() {
        int port = Integer.parseInt(environment.getProperty("local.server.port"));
        baseUrl = "http://localhost:" + port;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .scope("profile")
                .scope("ecommerce.api")
                .scope("identity.admin")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        clients.save(client);

        administratorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                administratorId,
                USERNAME,
                passwordEncoder.encode(PASSWORD));
        jdbc.update(
                "INSERT INTO administrator_roles(administrator_id, role_id) "
                        + "SELECT ?, id FROM roles WHERE code = 'SUPER_ADMIN'",
                administratorId);
        operatorAdministratorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO administrators(id, username, password_hash, enabled) VALUES (?, ?, ?, TRUE)",
                operatorAdministratorId,
                "operator.admin",
                passwordEncoder.encode(PASSWORD));
        jdbc.update(
                "INSERT INTO administrator_roles(administrator_id, role_id) "
                        + "SELECT ?, id FROM roles WHERE code = 'SUPER_ADMIN'",
                operatorAdministratorId);
    }

    @AfterEach
    void cleanDatabase() {
        jdbc.update("UPDATE refresh_token_families SET current_token_digest = NULL");
        jdbc.update("DELETE FROM refresh_token_history");
        jdbc.update("DELETE FROM refresh_token_families");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_registered_client WHERE client_id = ?", CLIENT_ID);
        jdbc.update("DELETE FROM administrator_audit");
        jdbc.update("DELETE FROM administrator_roles");
        jdbc.update("DELETE FROM administrators WHERE id IN (?, ?)", administratorId, operatorAdministratorId);
    }

    @AfterAll
    static void deleteGeneratedKeyFile() throws Exception {
        Files.deleteIfExists(JWK_SET_FILE);
    }

    @Test
    void issuesPkceTokensWithConsistentUuidSubjectAndAudienceScopedPermissions() throws Exception {
        String state = "state-123";
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        String authorizeUrl = baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&scope=" + encode("openid profile ecommerce.api identity.admin")
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256"
                + "&ui_locales=vi-VN";

        HttpResponse<String> authorize = getHtml(authorizeUrl);
        assertThat(authorize.statusCode()).isEqualTo(302);
        assertThat(authorize.headers().firstValue("Location").orElseThrow()).contains("/login");

        HttpResponse<String> loginPage = get(baseUrl + "/login");
        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThat(loginPage.body()).contains("lang=\"vi\"", "Đăng nhập Commerce");
        assertThat(loginPage.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String csrf = csrfToken(loginPage.body());
        HttpResponse<String> csrfRejected = postForm(baseUrl + "/login", "username=" + encode(USERNAME));
        assertThat(csrfRejected.statusCode()).isEqualTo(302);
        assertThat(csrfRejected.headers().firstValue("Location").orElseThrow()).contains("/login");

        String loginBody = "username=" + encode(USERNAME) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(csrf);
        HttpResponse<String> login = postForm(baseUrl + "/login", loginBody);
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElseThrow()).contains("/oauth2/authorize?");
        assertThat(login.headers().firstValue("Location").orElseThrow()).contains("state-123");
        assertThat(get(baseUrl + "/login").body()).contains("lang=\"vi\"", "Đăng nhập Commerce");

        HttpResponse<String> authorized = get(authorizeUrl);
        assertThat(authorized.statusCode()).isEqualTo(302);
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        Map<String, String> callbackParameters = query(callback.getRawQuery());
        assertThat(callbackParameters.get("state")).isEqualTo(state);
        String code = callbackParameters.get("code");
        assertThat(code).isNotBlank();

        String form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&code_verifier=" + encode(VERIFIER);
        String basic =
                Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> tokenResponse = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/token"))
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);

        JSONObject tokenBody = new JSONObject(tokenResponse.body());
        SignedJWT accessToken = SignedJWT.parse(tokenBody.getString("access_token"));
        assertThat(accessToken.verify(new RSASSAVerifier(SIGNING_KEY.toRSAPublicKey())))
                .isTrue();
        var accessClaims = accessToken.getJWTClaimsSet();
        assertThat(accessClaims.getIssuer()).isEqualTo(ISSUER);
        assertThat(accessClaims.getSubject()).isEqualTo(administratorId.toString());
        assertThat(accessClaims.getAudience()).containsExactlyInAnyOrder("ecommerce-api", "identity-api");
        assertThat(accessClaims.getStringListClaim("roles")).contains("SUPER_ADMIN");
        assertThat(accessClaims.getStringListClaim("permissions")).contains("administrator:read", "entitlement:write");
        assertThat(accessClaims.getStringClaim("scope")).isEqualTo("ecommerce.api identity.admin openid profile");

        SignedJWT idToken = SignedJWT.parse(tokenBody.getString("id_token"));
        assertThat(idToken.getJWTClaimsSet().getSubject()).isEqualTo(accessClaims.getSubject());
        assertThat(idToken.getJWTClaimsSet().getAudience()).contains(CLIENT_ID);

        HttpResponse<String> userInfo = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/userinfo"))
                        .header("Authorization", "Bearer " + tokenBody.getString("access_token"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(userInfo.statusCode()).isEqualTo(200);
        assertThat(new JSONObject(userInfo.body()).getString("sub")).isEqualTo(administratorId.toString());

        HttpResponse<String> jwks = get(baseUrl + "/oauth2/jwks");
        JSONArray keys = new JSONObject(jwks.body()).getJSONArray("keys");
        assertThat(keys.length()).isEqualTo(1);
        assertThat(keys.getJSONObject(0).getString("kid")).isEqualTo(KEY_ID);
        assertThat(keys.getJSONObject(0).has("d")).isFalse();
    }

    @Test
    void returnsTheSameGenericLoginFailureForUnknownAndDisabledAdministrators() throws Exception {
        HttpResponse<String> unknown = submitLogin("missing.admin", "not-the-password");
        assertThat(unknown.statusCode()).isEqualTo(302);
        assertThat(unknown.headers().firstValue("Location").orElseThrow()).contains("/login?error");

        jdbc.update("UPDATE administrators SET enabled = FALSE WHERE id = ?", administratorId);
        HttpResponse<String> disabled = submitLogin(USERNAME, PASSWORD);
        assertThat(disabled.statusCode()).isEqualTo(302);
        assertThat(disabled.headers().firstValue("Location").orElseThrow()).contains("/login?error");
        assertThat(unknown.headers().firstValue("Location"))
                .isEqualTo(disabled.headers().firstValue("Location"));
    }

    @Test
    void localizesLoginFromHintsAndAcceptLanguageWithoutExposingFailureDetails() throws Exception {
        HttpResponse<String> authorization =
                get(authorizationRequestUrl("locale-vi", "openid") + "&ui_locales=fr-FR%20vi-VN");
        assertThat(authorization.statusCode()).isEqualTo(302);
        assertThat(authorization.headers().firstValue("Location").orElseThrow()).contains("/login");

        HttpResponse<String> vietnamese = get(baseUrl + "/login");
        assertThat(vietnamese.body()).contains("lang=\"vi\"", "Đăng nhập Commerce", "Tên đăng nhập");
        assertThat(vietnamese.body())
                .contains("href=\"https://backoffice.test/\"", "aria-label=\"Quay về Commerce Backoffice\"");
        String failedForm = "username=" + encode("missing.admin") + "&password=" + encode("invalid") + "&_csrf="
                + encode(csrfToken(vietnamese.body()));
        HttpResponse<String> failed = postForm(baseUrl + "/login", failedForm);
        assertThat(failed.statusCode()).isEqualTo(302);
        assertThat(failed.headers().firstValue("Location").orElseThrow()).contains("/login?error");
        HttpResponse<String> failurePage = get(baseUrl + "/login?error");
        assertThat(failurePage.body())
                .contains("lang=\"vi\"", "Không thể đăng nhập")
                .doesNotContain("missing.admin", "invalid");

        get(authorizationRequestUrl("locale-en", "openid") + "&ui_locales=en-US");
        HttpResponse<String> englishOverride = get(baseUrl + "/login");
        assertThat(englishOverride.body()).contains("lang=\"en\"", "Sign in to Commerce");
        assertThat(get(baseUrl + "/login-assets/identity.css").statusCode()).isEqualTo(200);
    }

    @Test
    void honorsVietnameseHintForBrowserOidcNavigationWithEnglishBrowserPreference() throws Exception {
        HttpResponse<String> authorization = http.send(
                HttpRequest.newBuilder(URI.create(authorizationRequestUrl("browser-locale", "openid profile")
                                + "&nonce=browser-nonce&ui_locales=vi"))
                        .header("Accept", "text/html")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authorization.statusCode()).isEqualTo(302);
        HttpResponse<String> login = getWithLanguage(baseUrl + "/login", "en-US,en;q=0.9");
        assertThat(login.body()).contains("lang=\"vi\"", "Đăng nhập Commerce");
    }

    @Test
    void usesSupportedAcceptLanguageThenFallsBackToEnglish() throws Exception {
        HttpResponse<String> unsupported = getWithLanguage(baseUrl + "/login", "fr-CA");
        assertThat(unsupported.body()).contains("lang=\"en\"", "Sign in to Commerce");

        HttpResponse<String> vietnamese = getWithLanguage(baseUrl + "/login", "fr-CA, vi-VN;q=0.8");
        assertThat(vietnamese.body()).contains("lang=\"vi\"", "Tên đăng nhập");

        HttpResponse<String> sessionPreference = getWithLanguage(baseUrl + "/login", "en-US");
        assertThat(sessionPreference.body()).contains("lang=\"vi\"", "Tên đăng nhập");
    }

    @Test
    void managementReadApiRequiresIdentityAudienceBearerTokenAndDoesNotExposeCredentials() throws Exception {
        JSONObject issued = issueTokenForState("management-read");
        String accessToken = issued.getString("access_token");
        HttpResponse<String> listed = getWithBearer(baseUrl + "/api/admin/administrators?page=0&size=20", accessToken);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body())
                .contains(USERNAME)
                .doesNotContain("password_hash")
                .doesNotContain(PASSWORD);

        HttpResponse<String> catalog = getWithBearer(baseUrl + "/api/admin/permissions", accessToken);
        assertThat(catalog.statusCode()).isEqualTo(200);

        HttpResponse<String> cookieOnly = get(baseUrl + "/api/admin/administrators");
        assertThat(cookieOnly.statusCode()).isEqualTo(401);

        JSONObject ecommerceOnly = issueTokenForState("ecommerce-only", "openid profile ecommerce.api");
        HttpResponse<String> wrongAudience =
                getWithBearer(baseUrl + "/api/admin/administrators", ecommerceOnly.getString("access_token"));
        assertThat(wrongAudience.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsTokensExpiredBeyondTheApprovedThirtySecondSkew() throws Exception {
        Instant now = Instant.now();
        SignedJWT expired = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(administratorId.toString())
                        .audience("identity-api")
                        .issueTime(Date.from(now.minusSeconds(120)))
                        .expirationTime(Date.from(now.minusSeconds(45)))
                        .build());
        expired.sign(new RSASSASigner(SIGNING_KEY));

        HttpResponse<String> response = getWithBearer(baseUrl + "/api/admin/administrators", expired.serialize());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void returnsFieldValidationAndSafeMalformedRequestProblems() throws Exception {
        String token = issueTokenForState("validation-errors").getString("access_token");
        HttpResponse<String> invalid = sendJson(
                "POST",
                baseUrl + "/api/admin/administrators",
                token,
                new JSONObject().put("username", " ").put("password", "secret").toString());
        JSONObject body = assertProblem(invalid, 400, "VALIDATION_FAILED");
        JSONArray errors = body.getJSONArray("errors");
        assertThat(errors.length()).isEqualTo(2);
        Set<String> pointers = new java.util.HashSet<>();
        for (int index = 0; index < errors.length(); index++) {
            JSONObject error = errors.getJSONObject(index);
            assertThat(error.getString("location")).isEqualTo("body");
            pointers.add(error.getString("pointer"));
            assertThat(error.has("rejectedValue")).isFalse();
        }
        assertThat(pointers).containsExactlyInAnyOrder("/username", "/password");
        assertThat(invalid.body()).doesNotContain("secret");

        HttpResponse<String> brokenJson = sendJson(
                "POST", baseUrl + "/api/admin/administrators", token, "{\"password\":\"do-not-echo-this-value\",");
        assertProblem(brokenJson, 400, "BAD_REQUEST");
        assertThat(brokenJson.body()).doesNotContain("do-not-echo-this-value");
        HttpResponse<String> malformed = getWithBearer(baseUrl + "/api/admin/administrators/not-a-uuid", token);
        assertProblem(malformed, 400, "BAD_REQUEST");
        assertThat(malformed.body()).doesNotContain("Failed to convert", "java.util.UUID");
    }

    @Test
    void returnsConsistentBusinessAndBearerProblemsWithoutChangingOauthErrors() throws Exception {
        String token = issueTokenForState("business-errors").getString("access_token");
        String missing = baseUrl + "/api/admin/administrators/" + UUID.randomUUID();
        assertProblem(getWithBearer(missing, token), 404, "ADMINISTRATOR_NOT_FOUND");
        assertProblem(
                sendJson("PUT", missing + "/enabled", token, "{\"enabled\":false}"), 404, "ADMINISTRATOR_NOT_FOUND");
        assertProblem(sendJson("PUT", missing + "/roles", token, "{\"roleCodes\":[]}"), 404, "ADMINISTRATOR_NOT_FOUND");
        assertProblem(
                sendJson(
                        "POST",
                        baseUrl + "/api/admin/administrators",
                        token,
                        new JSONObject()
                                .put("username", USERNAME)
                                .put("password", PASSWORD)
                                .toString()),
                409,
                "DUPLICATE_ADMINISTRATOR");

        jdbc.update("DELETE FROM administrator_roles WHERE administrator_id = ?", operatorAdministratorId);
        assertProblem(
                sendJson(
                        "PUT",
                        baseUrl + "/api/admin/administrators/" + administratorId + "/enabled",
                        token,
                        "{\"enabled\":false}"),
                409,
                "LAST_PRIVILEGED_ADMINISTRATOR");

        HttpResponse<String> unauthenticated = get(baseUrl + "/api/admin/administrators");
        assertProblem(unauthenticated, 401, "UNAUTHENTICATED");
        assertThat(unauthenticated.headers().firstValue("WWW-Authenticate").orElseThrow())
                .startsWith("Bearer");
        jdbc.update("DELETE FROM administrator_roles WHERE administrator_id = ?", administratorId);
        assertProblem(
                sendJson(
                        "POST",
                        baseUrl + "/api/admin/administrators",
                        token,
                        new JSONObject()
                                .put("username", "another.admin")
                                .put("password", PASSWORD)
                                .toString()),
                403,
                "FORBIDDEN");

        JSONObject oauthError = refresh("unknown-refresh-token").getJSONObject("body");
        assertThat(oauthError.getString("error")).isEqualTo("invalid_grant");
        assertThat(oauthError.has("type")).isFalse();
        assertThat(oauthError.has("code")).isFalse();
    }

    private JSONObject assertProblem(HttpResponse<String> response, int status, String code)
            throws org.json.JSONException {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        JSONObject problem = new JSONObject(response.body());
        assertThat(problem.getInt("status")).isEqualTo(status);
        assertThat(problem.getString("code")).isEqualTo(code);
        assertThat(problem.getString("type")).startsWith("urn:ecommerce:problem:");
        assertThat(problem.get("detail")).isInstanceOf(String.class);
        assertThat(problem.has("trace")).isFalse();
        return problem;
    }

    @Test
    void consumesAuthorizationCodeAtomicallyAndRevokesItsRefreshFamilyOnReplay() throws Exception {
        String code = authorizationCodeForState("concurrent-code", "openid profile ecommerce.api identity.admin");
        String form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&code_verifier=" + encode(VERIFIER);
        HttpRequest tokenRequest = tokenRequestBuilder(form);

        CompletableFuture<HttpResponse<String>> first =
                http.sendAsync(tokenRequest, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second =
                http.sendAsync(tokenRequest, HttpResponse.BodyHandlers.ofString());
        List<HttpResponse<String>> responses = List.of(first.join(), second.join());

        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 400);
        HttpResponse<String> successfulResponse = responses.stream()
                .filter(response -> response.statusCode() == 200)
                .findFirst()
                .orElseThrow();
        HttpResponse<String> rejectedResponse = responses.stream()
                .filter(response -> response.statusCode() == 400)
                .findFirst()
                .orElseThrow();
        JSONObject successfulBody = new JSONObject(successfulResponse.body());
        JSONObject rejectedBody = new JSONObject(rejectedResponse.body());
        assertThat(rejectedBody.getString("error")).isEqualTo("invalid_grant");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE authorization_id = "
                                + "(SELECT id FROM oauth2_authorization WHERE authorization_code_value = ?)",
                        String.class,
                        code))
                .isEqualTo("REVOKED");
        assertThat(refresh(successfulBody.getString("refresh_token")).getInt("status"))
                .isEqualTo(400);
    }

    @Test
    void rejectsRefreshTokensWhoseAuthorizationHasNoLineageFamily() throws Exception {
        JSONObject issued = issueTokenForState("missing-lineage");
        String refreshToken = issued.getString("refresh_token");
        jdbc.update(
                "UPDATE refresh_token_families SET current_token_digest = NULL WHERE authorization_id = "
                        + "(SELECT id FROM oauth2_authorization WHERE refresh_token_value = ?)",
                refreshToken);
        jdbc.update(
                "DELETE FROM refresh_token_history WHERE family_id = "
                        + "(SELECT family_id FROM refresh_token_families WHERE authorization_id = "
                        + "(SELECT id FROM oauth2_authorization WHERE refresh_token_value = ?))",
                refreshToken);
        jdbc.update(
                "DELETE FROM refresh_token_families WHERE authorization_id = "
                        + "(SELECT id FROM oauth2_authorization WHERE refresh_token_value = ?)",
                refreshToken);

        JSONObject rejected = refresh(refreshToken);
        assertThat(rejected.getInt("status")).isEqualTo(400);
        assertThat(rejected.getJSONObject("body").getString("error")).isEqualTo("invalid_grant");
    }

    @Test
    void replayRevokesRetainedFamilyAndAuditsWhenAuthorizationWasDeleted() throws Exception {
        JSONObject issued = issueTokenForState("retained-lineage");
        String consumedToken = issued.getString("refresh_token");
        JSONObject rotated = refresh(consumedToken);
        assertThat(rotated.getInt("status")).isEqualTo(200);
        String currentToken = rotated.getJSONObject("body").getString("refresh_token");
        String authorizationId = jdbc.queryForObject(
                "SELECT id FROM oauth2_authorization WHERE refresh_token_value = ?", String.class, currentToken);
        // Simulate retention/cleanup outside the normal remove() path, which already revokes families.
        jdbc.update("DELETE FROM oauth2_authorization WHERE id = ?", authorizationId);

        JSONObject replay = refresh(consumedToken);

        assertThat(replay.getInt("status")).isEqualTo(400);
        assertThat(replay.getJSONObject("body").getString("error")).isEqualTo("invalid_grant");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE authorization_id = ?",
                        String.class,
                        authorizationId))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(refresh(currentToken).getInt("status")).isEqualTo(400);
        assertThat(refresh(consumedToken).getInt("status")).isEqualTo(400);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void managementWritesReturnSafeDtosAndRecheckTheActorAgainstCurrentDatabaseState() throws Exception {
        String accessToken = issueTokenForState("management-write").getString("access_token");
        HttpResponse<String> created = sendJson(
                "POST",
                baseUrl + "/api/admin/administrators",
                accessToken,
                new JSONObject()
                        .put("username", "managed.creation")
                        .put("password", "Managed-test-password-123")
                        .toString());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("managed.creation").doesNotContain("password");
        UUID createdId = UUID.fromString(new JSONObject(created.body()).getString("id"));

        HttpResponse<String> disabled = sendJson(
                "PUT",
                baseUrl + "/api/admin/administrators/" + createdId + "/enabled",
                accessToken,
                "{\"enabled\":false}");
        assertThat(disabled.statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE actor_administrator_id = ? "
                                + "AND target_administrator_id = ? AND action = 'ADMINISTRATOR_DISABLED'",
                        Integer.class,
                        administratorId,
                        createdId))
                .isEqualTo(1);

        administratorService.replaceRoles(operatorAdministratorId, administratorId, Set.of());
        HttpResponse<String> staleActor = sendJson(
                "POST",
                baseUrl + "/api/admin/administrators",
                accessToken,
                new JSONObject()
                        .put("username", "stale.actor")
                        .put("password", "Stale-actor-password-123")
                        .toString());
        assertThat(staleActor.statusCode()).isEqualTo(403);
    }

    @Test
    void rejectedLastAdministratorDisableRollsBackGrantRevocation() throws Exception {
        JSONObject grant = issueTokenForState("rollback-disable");
        String refreshToken = grant.getString("refresh_token");
        jdbc.update("DELETE FROM administrator_roles WHERE administrator_id = ?", operatorAdministratorId);

        assertThatThrownBy(() -> administratorService.setEnabled(administratorId, administratorId, false))
                .isInstanceOf(LastAdministratorException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT enabled FROM administrators WHERE id = ?", Boolean.class, administratorId))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE administrator_id = ?",
                        String.class,
                        administratorId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE action = 'ADMINISTRATOR_DISABLED'",
                        Integer.class))
                .isZero();
        assertThat(refresh(refreshToken).getInt("status")).isEqualTo(200);
    }

    @Test
    void disablingAndReEnablingAdministratorDoesNotRestoreRefreshFamilies() throws Exception {
        JSONObject initialGrant = issueTokenForState("disable-admin");
        String oldRefreshToken = initialGrant.getString("refresh_token");

        administratorService.setEnabled(operatorAdministratorId, administratorId, false);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE administrator_id = ?",
                        String.class,
                        administratorId))
                .isEqualTo("REVOKED");
        HttpResponse<String> oldSession = get(authorizationRequestUrl("disabled-session", "openid profile"));
        assertThat(oldSession.statusCode()).isEqualTo(302);
        assertThat(oldSession.headers().firstValue("Location").orElseThrow()).contains("/login?error");

        JSONObject rejectedRefresh = refresh(oldRefreshToken);
        assertThat(rejectedRefresh.getInt("status")).isEqualTo(400);
        administratorService.setEnabled(operatorAdministratorId, administratorId, true);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE administrator_id = ?",
                        String.class,
                        administratorId))
                .isEqualTo("REVOKED");

        JSONObject newGrant = issueTokenForState("re-enabled-admin");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM refresh_token_families WHERE authorization_id = "
                                + "(SELECT id FROM oauth2_authorization WHERE refresh_token_value = ?)",
                        String.class,
                        newGrant.getString("refresh_token")))
                .isEqualTo("ACTIVE");
        assertThat(refresh(oldRefreshToken).getInt("status")).isEqualTo(400);
    }

    @Test
    void rotatesRefreshTokensAndCommitsFamilyRevocationBeforeReturningReplayError() throws Exception {
        JSONObject firstGrant = issueTokenForState("refresh-first");
        JSONObject independentGrant = issueTokenForState("refresh-independent");
        String firstToken = firstGrant.getString("refresh_token");
        String successor = refresh(firstToken).getJSONObject("body").getString("refresh_token");
        assertThat(successor).isNotEqualTo(firstToken);

        JSONObject replay = refresh(firstToken);
        assertThat(replay.getInt("status")).isEqualTo(400);
        assertThat(replay.getJSONObject("body").getString("error")).isEqualTo("invalid_grant");

        JSONObject independentRotation = refresh(independentGrant.getString("refresh_token"));
        assertThat(independentRotation.getInt("status")).isEqualTo(200);

        JSONObject revokedSuccessor = refresh(successor);
        assertThat(revokedSuccessor.getInt("status")).isEqualTo(400);
        assertThat(revokedSuccessor.getJSONObject("body").getString("error")).isEqualTo("invalid_grant");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_token_families WHERE status = 'REVOKED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM administrator_audit WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED' "
                                + "AND outcome = 'DETECTED'",
                        Integer.class))
                .isEqualTo(1);
    }

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithLanguage(String url, String language) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Accept-Language", language)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithBearer(String url, String accessToken) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendJson(String method, String url, String accessToken, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String url, String body) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> submitLogin(String username, String password) throws Exception {
        HttpResponse<String> loginPage = get(baseUrl + "/login");
        String body = "username=" + encode(username)
                + "&password=" + encode(password)
                + "&_csrf=" + encode(csrfToken(loginPage.body()));
        return postForm(baseUrl + "/login", body);
    }

    private JSONObject issueTokenForState(String state) throws Exception {
        return issueTokenForState(state, "openid profile ecommerce.api identity.admin");
    }

    private JSONObject issueTokenForState(String state, String scope) throws Exception {
        String code = authorizationCodeForState(state, scope);
        String form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&code_verifier=" + encode(VERIFIER);
        JSONObject token = tokenRequest(form);
        assertThat(token.getInt("status")).isEqualTo(200);
        return token.getJSONObject("body");
    }

    private String authorizationCodeForState(String state, String scope) throws Exception {
        String authorizeUrl = authorizationRequestUrl(state, scope);
        HttpResponse<String> authorized = get(authorizeUrl);
        if (authorized.headers().firstValue("Location").orElse("").contains("/login")) {
            HttpResponse<String> loginPage = get(baseUrl + "/login");
            String body = "username=" + encode(USERNAME)
                    + "&password=" + encode(PASSWORD)
                    + "&_csrf=" + encode(csrfToken(loginPage.body()));
            postForm(baseUrl + "/login", body);
            authorized = get(authorizeUrl);
        }
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        return query(callback.getRawQuery()).get("code");
    }

    private String authorizationRequestUrl(String state, String scope) throws Exception {
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&scope=" + encode(scope)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
    }

    private JSONObject refresh(String refreshToken) throws Exception {
        return tokenRequest("grant_type=refresh_token&refresh_token=" + encode(refreshToken));
    }

    private JSONObject tokenRequest(String form) throws Exception {
        HttpResponse<String> response = http.send(tokenRequestBuilder(form), HttpResponse.BodyHandlers.ofString());
        return new JSONObject().put("status", response.statusCode()).put("body", new JSONObject(response.body()));
    }

    private HttpRequest tokenRequestBuilder(String form) {
        String basic =
                Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
    }

    private HttpResponse<String> getHtml(String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Accept", "text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken(String html) {
        var input = java.util.regex.Pattern.compile("<input\\b(?=[^>]*\\bname=\"_csrf\")[^>]*>")
                .matcher(html);
        var matcher = java.util.regex.Pattern.compile("\\bvalue=\"([^\"]+)\"");
        boolean found = false;
        String token = null;
        while (input.find()) {
            var field = matcher.matcher(input.group());
            if (field.find()) {
                found = true;
                token = field.group(1);
                break;
            }
        }
        assertThat(found).isTrue();
        return token;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> query(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        Arrays.stream(rawQuery.split("&")).forEach(pair -> {
            String[] parts = pair.split("=", 2);
            result.put(
                    java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        });
        return result;
    }
}
