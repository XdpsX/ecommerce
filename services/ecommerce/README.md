# Ecommerce API

Ecommerce is configured as a stateless OAuth 2.0 resource server. It validates JWT signatures with the Identity JWKS, checks the configured issuer and `ecommerce-api` audience, restricts signatures to RS256, and maps the `permissions` and `scope` claims to authorities. Role and scope authorities do not grant application permissions.

Configure `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI`, and `IDENTITY_AUDIENCE` for the environment. Defaults target the local Identity service. The production application currently exposes no business API; unknown routes are denied. The security probe exists only in test sources.

REST errors follow the shared [HTTP error contract](../../docs/04-contracts/http-errors.md). `error/ApiProblems` writes Problem Details for security-filter failures; `error/ApiExceptionHandler` handles MVC failures on `/api/**` and preserves non-API handling. Missing/invalid bearer credentials return 401 with a Bearer challenge; insufficient permissions return 403. Error bodies do not expose JWT decoder diagnostics or exception details. The shared schema lives in `contracts/openapi/common/problem.yaml`; there is no shared Java error library.

The Ecommerce Failsafe suite validates the resource-server boundary and starts a packaged Identity JAR for a real Authorization Code and PKCE flow. Run `./services/identity/mvnw -f services/identity/pom.xml -DskipTests package` before `./services/ecommerce/mvnw -f services/ecommerce/pom.xml verify`; both commands need Docker for integration fixtures. Ecommerce CI builds the Identity JAR before running its verification.
