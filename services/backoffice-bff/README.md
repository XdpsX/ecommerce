# Backoffice BFF

Backoffice browser entry point following [ADR-0001](../../docs/03-architecture/adr/0001-backoffice-bff-as-api-gateway.md): Gateway WebFlux, confidential OIDC code + PKCE S256, Redis Spring Session and server-side TokenRelay. Java 25, Boot 4.1.2 and Cloud 2025.1.4 are pinned; the [Spring compatibility matrix](https://spring.io/projects/spring-cloud/) supports Boot 4.1 on this release line. Do not disable its compatibility verifier.

## Browser boundary

The checked-in [OpenAPI contract](../../contracts/openapi/backoffice-bff.yaml) defines session, CSRF and local logout. OAuth navigation starts at `/oauth2/authorization/identity` and callbacks use `/login/oauth2/code/identity`. The browser may include `ui_locales=en` or `ui_locales=vi`; the BFF forwards only the first supported locale (including supported regional variants) to Identity while retaining PKCE parameters. `returnTo` is optional and omitted for the default `/`; non-default local paths are independently validated by the BFF. Only minimal subject data reaches the frontend. Tokens and client secrets stay on the server. The authorized client is stored in the same Redis-backed WebSession used by login, not an in-memory principal-keyed service. The relay manager reads that repository without refresh support.

The session is usable only while the earlier of the token response expiry and Identity JWT `exp` is strictly after the server clock, with zero extra skew. This prevents response transport/rounding from extending JWT validity. The JWT comes only from the confidential token exchange; parsing `exp` can only shorten validity and never supplies authentication or permissions. A session/API check at or after expiry invalidates it and returns 401. Redis also enforces a 30-minute idle bound, including incomplete/anonymous sessions; this does not extend five-minute token validity. No timer or browser token parsing is involved. Reauthenticate when the token is unusable. Existing disabled-account and JWT permission-snapshot behavior remains Identity/resource-server owned.

Logout requires CSRF, invalidates local session and authorized-client state and returns 204. **Identity SSO may remain active**, so the next sign-in may succeed without another credential prompt. This does not promise revocation of previously issued downstream JWTs. Missing/invalid CSRF is 403 `CSRF_INVALID`; permission denial is 403 `FORBIDDEN`; infrastructure failure is unavailable, not a successful logout. Never automatically replay unsafe requests.

Only `/api/ecommerce/**` routes to Ecommerce `/api/**`. Client Authorization and identity/forwarded headers are stripped; TokenRelay supplies the server token. Browser cookies/CSRF headers are not forwarded and downstream Set-Cookie is removed. No production business/probe endpoint is added. Resource servers still enforce their own authentication/permissions.

## Local setup

Start the existing PostgreSQL/Redis Compose services. Build/configure Identity with its signing keys and bootstrap an administrator using the existing interactive operator commands in the [Identity guide](../identity/README.md). Register the confidential `backoffice-bff` client with callback `http://localhost:3000/login/oauth2/code/identity`, scopes `openid,profile,ecommerce.api` and **refresh disabled**. Use the same strong secret in the registration prompt and the BFF environment; never place it in VITE variables or Git. Existing client settings are not silently overwritten.

Set `BFF_CLIENT_SECRET`, `BFF_REDIS_PASSWORD` (same local Redis credential), `IDENTITY_ISSUER`, and optionally `BFF_REDIS_HOST`, `BFF_REDIS_PORT`, `BFF_ECOMMERCE_URI`. Local BFF uses port 8081 and public browser origin `http://localhost:3000`. Run from `services/backoffice-bff`:

```powershell
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

The local profile imports root `.env` with this working directory. Vite on port 3000 proxies BFF and OAuth callback paths; use `localhost` consistently. The public callback is explicit, so caller Host/Forwarded headers do not determine redirect destinations. `server.forward-headers-strategy=none` ignores proxy headers. Production ingress must preserve the explicit public origin, use HTTPS and strip untrusted forwarded headers; BFF does not infer trust from arbitrary client headers. Production cookies are host-only, HttpOnly, Secure, SameSite=Lax, Path=/ and named `BACKOFFICE_SESSION` to avoid the Identity cookie name. HTTP cookies are allowed only in the explicit local profile; never deploy that profile publicly.

## Verification

From repository root, with Java 25, Docker and process-local UTC for JDBC fixtures:

```powershell
& .\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package
& .\services\backoffice-bff\mvnw.cmd -f services/backoffice-bff/pom.xml -B --no-transfer-progress verify
```

The integration fixture runs packaged Identity against disposable PostgreSQL and BFF against disposable Redis, uses a JWT-validating test-only downstream, and checks PKCE, Redis session/client survival across BFF restart, expiry and logout replay. Test credentials/keys are disposable; no developer database is changed. Frontend HTTP mocks are separate confidence from real OAuth integration. Record browser checks and any unexecuted verification explicitly.
