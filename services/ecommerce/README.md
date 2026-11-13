# Ecommerce API

Ecommerce is configured as a stateless OAuth 2.0 resource server. It validates JWT signatures with the Identity JWKS, checks the configured issuer and `ecommerce-api` audience, restricts signatures to RS256, and maps the `permissions` and `scope` claims to authorities. Role and scope authorities do not grant application permissions.

Configure `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI`, and `IDENTITY_AUDIENCE` for the environment. Defaults target the local Identity service. The production application currently exposes no business API; unknown routes are denied. The security probe exists only in test sources.

Ecommerce owns its PostgreSQL database and Flyway history. It uses Spring JDBC, Bean Validation, Flyway and the PostgreSQL driver. Configure `ECOMMERCE_DB_URL` (default `jdbc:postgresql://localhost:5432/ecommerce`), `ECOMMERCE_DB_USERNAME` (default `ecommerce`) and the required `ECOMMERCE_DB_PASSWORD`. The `local` profile imports the repository `.env` when the process working directory is `services/ecommerce`; Maven `spring-boot:run` uses that directory by default. IDEs should use the same working directory, or provide the variables directly. Other profiles do not import `.env`.

The first migration, `V1__ecommerce_foundation.sql`, intentionally contains only `SELECT 1;` to establish and verify the migration history without creating business tables. Do not edit an applied migration; add later schema changes as new numbered migrations. Do not baseline an unknown database or enable schema generation. Provision an empty Ecommerce database before startup; local Compose creates it on a fresh volume and the [infrastructure guide](../../infra/README.md) covers existing volumes.

Issue #23's approved Category design uses Spring Data JPA + Hibernate, JPA `@Version` and optimistic structure revision, with Flyway continuing to own migrations. This is planned work; the current foundation still uses JDBC and exposes no Category API. The change is scoped to Ecommerce. Category will store `imageAssetId` under the [independent Media reference policy](../../docs/03-architecture/media-service.md#consumer-image-references): Media can delete the asset, and consumers must handle unavailable images with placeholders.

With PostgreSQL running and `.env` configured, start the service from its directory so the `local` profile resolves the root `.env` import. If the application JVM timezone is `Asia/Saigon`, PostgreSQL 18 rejects the JDBC connection. Set UTC for the application process:

```powershell
Set-Location services/ecommerce
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

For an IDE, set `JAVA_TOOL_OPTIONS` to `-Duser.timezone=UTC` in the Run/Debug environment, or add `-Duser.timezone=UTC` to the application's VM options.

REST errors follow the shared [HTTP error contract](../../docs/04-contracts/http-errors.md). `shared/error/ApiProblems` writes Problem Details for security-filter failures; `shared/error/GlobalExceptionHandler` handles MVC failures on `/api/**` and preserves non-API handling. Missing/invalid bearer credentials return 401 with a Bearer challenge; insufficient permissions return 403. Error bodies do not expose JWT decoder diagnostics or exception details. The shared schema lives in `contracts/openapi/common/problem.yaml`; there is no shared Java error library.

## Package organization

The root namespace remains `com.ecommerce.api`. Spring composition and resource-server wiring live in `config/ResourceServerSecurityConfiguration`; service-wide HTTP error handling lives in `shared/error`. The application entry point stays at the root. No business feature or empty shared-security packages are created before they are needed.

HTTP integration tests mirror the web adapter package. `ArchitectureBoundaryTest` checks HTTP adapter and shared error placement while allowing composition code in `config` to reference Spring HTTP/security APIs, and keeps persistence dependencies out of the web boundary. Security and validation probes and their route customization remain test-only; the production application continues to deny unknown routes. `PersistenceFoundationIT` uses PostgreSQL 18.6 to verify the migration history and transaction behavior.

## Verification

The Ecommerce Failsafe suite validates persistence and the resource-server boundary, then starts a packaged Identity JAR for a real Authorization Code and PKCE flow. Run from the repository root:

If the JVM timezone is `Asia/Saigon`, PostgreSQL 18 rejects it during JDBC connection setup. Set UTC for Maven and its child test processes before verification:

```powershell
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  & .\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package
  & .\services\ecommerce\mvnw.cmd -f services/ecommerce/pom.xml -B --no-transfer-progress verify
} finally {
  if ($null -eq $previousJavaToolOptions) {
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
  } else {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
  }
}
```

Both commands need Java 25; Ecommerce integration tests need Docker. Ecommerce CI builds Identity before verification. See the [code organization convention](../../docs/06-development/code-organization.md) and [HTTP error contract](../../docs/04-contracts/http-errors.md) for package and REST error rules.
