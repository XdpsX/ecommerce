# Identity Service

Identity owns administrator credentials, role and permission assignments, OAuth/OIDC authorizations, refresh-token lineage, and security audit records. It serves Authorization Code with PKCE and signs RS256 JWTs. Ecommerce validates those JWTs independently as a resource server. The Backoffice BFF and its browser session remain outside this change.

## Code organization

Packages follow the [backend code organization convention](../../docs/06-development/code-organization.md): root `config` for composition, service-local `shared/error` for HTTP error handling, and feature-owned implementations below. `GlobalExceptionHandler` and `ApiProblems` live in `shared/error`; administrator business exceptions remain with the admin application and OAuth2 protocol implementations remain with authorization.

Current capability organization:

- `admin/domain` contains account and entitlement models. `admin/application/service` owns management/bootstrap use cases and the `AdministratorDirectory` collaboration facade; `admin/application/port` contains repository, query and revocation abstractions. Related administrator exceptions stay in `admin/application/exception`.
- Administrator HTTP/CLI adapters live in `admin/adapter/web` and `admin/adapter/cli`; JDBC implementations live in `admin/adapter/persistence`. `shared/error/GlobalExceptionHandler` and `ApiProblems` map API and security failures to the shared [HTTP error contract](../../docs/04-contracts/http-errors.md). Advice handles only `/api/**`; OAuth2 errors and browser login redirects retain their protocol behavior.
- `AdministratorDirectory` is the admin-owned collaboration API for authorization: account lookup, credentials, entitlements, audit and account row locks. Lock/audit methods require an existing transaction so authorization operations retain their account-before-family-before-authorization lock order.
- `AccountGrantRevocation` is an application-owned port. Authorization implements it synchronously within the administrator-disable transaction, including rollback if the last-admin invariant rejects the change.
- `authorization/adapter/oauth2` groups framework-specific OAuth2 providers/filters, login, client registration, token claims, grant lifecycle and persistence. These technical adapters use Spring Authorization Server models directly rather than inventing a separate domain layer. `config` owns properties, bean composition, signing keys, authorization-server setup and the separately composed management API decoder/security chain. CLI adapters call the `authorization/application/port/ClientRegistration` contract.
- Package-private OAuth2 providers/filters remain beside their implementation; the narrow `OAuth2Components` factory exposes only framework interfaces needed by root composition. `RegisteredClientConfiguration` remains available when the authorization server is disabled so operator commands can use the JDBC client repository. Tests mirror source packages; `ArchitectureBoundaryTest` checks dependency and table-ownership boundaries without extra architecture tooling.

Package moves do not change endpoint paths, JWT claims, configuration keys, SQL migrations or CLI arguments. REST errors now have a defined Problem Details body; update operations return 404 when the target administrator does not exist. Browser authentication through the BFF remains separate work.

## Database and migrations

Provision a PostgreSQL database named `identity`; local Compose creates it owned by `POSTGRES_USER`. Set `IDENTITY_DB_URL` (default `jdbc:postgresql://localhost:5432/identity`), `IDENTITY_DB_USERNAME` to `POSTGRES_USER`, and `IDENTITY_DB_PASSWORD` to `POSTGRES_PASSWORD` in the process environment. Flyway uses that account to apply the versioned schema and role/permission catalog; it never seeds administrator credentials.

From the repository root, build and verify with `./services/identity/mvnw -f services/identity/pom.xml verify` (or `services\identity\mvnw.cmd` on Windows). The integration suite uses Testcontainers and needs Docker. The service CI workflow runs the same Maven verification and uploads Surefire and Failsafe reports.

The `identity_security_state` singleton serializes bootstrap and privileged administrator mutations. Mutations recheck the actor's enabled state, `SUPER_ADMIN` role and current permission in the database, and roll back if they would remove the last enabled administrator with the required management permissions.

## Signing key and confidential client

The authorization server is enabled by default. Set `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_PATH` and `IDENTITY_ACTIVE_KEY_ID`; mount the JWK set from a secret store as a read-only file. The active key must be a private RSA signing key of at least 2048 bits using RS256 and the configured `kid`. Startup fails when the key configuration is missing or invalid. The service does not generate or replace signing keys at startup. `IDENTITY_SERVER_ENABLED=false` is intended only for non-web commands and isolated tests.

Configure exact callback allowlists with `IDENTITY_CLIENT_REDIRECT_URIS` and `IDENTITY_CLIENT_POST_LOGOUT_REDIRECT_URIS`, plus `IDENTITY_CLIENT_ID` and `IDENTITY_CLIENT_NAME`. Register the confidential client from an interactive terminal after migrations:

```sh
java -jar services/identity/target/identity-0.0.1-SNAPSHOT.jar --identity.command=register-client
```

The command prompts for the secret and stores only its password hash. Repeating registration is a no-op only when the existing settings match; mismatches fail without overwriting the client. Authorization Code with PKCE S256 is the default. Refresh grant registration is disabled unless `IDENTITY_REFRESH_GRANT_ENABLED=true`; enabled refresh grants rotate tokens and revoke a family when a consumed ancestor is replayed.

For local `.env` settings, run from `services/identity` after packaging:

```powershell
java -Duser.timezone=UTC -jar target/identity-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --identity.command=register-client
```

Enter the same secret configured as `BFF_CLIENT_SECRET`. Setting `IDENTITY_CLIENT_*` in `.env` does not register the client automatically. Confirm the callback matches the BFF browser callback before starting sign-in.

Migration V6 adds the `media:read`, `media:upload` and `media:delete` permissions for audience `media-api`, initially assigned to `SUPER_ADMIN`. Migration V7 adds `category:read`, `category:write` and `category:delete` for `ecommerce-api` to `SUPER_ADMIN`. Existing tokens remain snapshots and must be reissued to include the new Category permissions. New registrations may request the `media.api` scope. To add that scope to one existing client without replacing its secret, callback URIs, grants or other scopes, run the explicit interactive operator command after migration:

```sh
java -jar services/identity/target/identity-0.0.1-SNAPSHOT.jar --identity.server-enabled=false --identity.command=add-media-scope --client-id=backoffice-bff
```

The command adds only the scope and writes an audit row with the target client ID. It is idempotent. Existing authorizations and tokens do not gain Media access automatically; the user must authorize the scope again.

Keep old public keys available long enough for all tokens signed with them to expire, including cache and clock-skew margins. Back up private keys and database independently. Do not regenerate a key automatically after loss.

## Initial administrator

After migrations have run, invoke the one-time command from an interactive terminal:

```sh
java -jar services/identity/target/identity-0.0.1-SNAPSHOT.jar --identity.command=bootstrap-admin
```

The command prompts for a username and password, stores a password hash, assigns `SUPER_ADMIN`, and writes the bootstrap marker and audit record in one transaction. Repeating it after successful bootstrap makes no changes. If administrator rows exist without a matching marker, it fails safely. Passwords are never accepted as command-line arguments; the command requires a real console and exits without starting HTTP services.

## HTTP and token behavior

The OAuth/OIDC server publishes discovery metadata, authorization, token, revocation, JWKS, UserInfo and logout endpoints. `/login` is a server-rendered, CSRF-protected English/Vietnamese form; a supported OIDC `ui_locales` hint takes precedence for that login, followed by the Identity session preference, a supported `Accept-Language`, then English. The locale survives failed credential attempts and session fixation. Login HTML is not cached, and its stylesheet is served from `/login-assets/**`. The request cache preserves only original GET authorization requests, so browser favicon or error requests cannot replace the pending OAuth callback flow. The administrator API is bearer-only under `/api/admin/**`; it requires an access token with `identity-api` audience and the matching permission claim. Browser session cookies do not authenticate these API routes. Backoffice uses the separate [BFF](../backoffice-bff/README.md) for its browser session; local BFF logout does not end Identity SSO.

## Login presentation

The login logo links back to the configured `IDENTITY_BACKOFFICE_URL`, independent of OAuth callback/return parameters. The local profile defaults to `http://localhost:3000`; set the trusted public Backoffice URL for deployment. Clicking it leaves the credential form without completing login or ending an existing Identity SSO session.

The standalone Thymeleaf login page uses the restrained green/light Backoffice palette and Geist Variable font. Its matching tokens are kept in `src/main/resources/static/login-assets/identity.css`; self-hosted Latin, extended Latin and Vietnamese font subsets plus their license are beside that stylesheet. This page does not load Backoffice JavaScript or build output. The [Backoffice README](../../web/backoffice/README.md#ui-primitives-and-visual-tokens) documents the corresponding semantic theme source.

Access tokens live for five minutes and carry the administrator UUID as `sub`, audience-scoped effective permissions, roles, scopes, issuer and expiry. Permissions are a token snapshot; Ecommerce accepts an already-issued JWT until expiry plus its configured clock skew. Account disable blocks old browser sessions and new token/refresh use, revokes active refresh families, and invalidates outstanding authorization codes and refresh tokens. Re-enabling an account does not revive old grants. Refresh tokens rotate on use with an eight-hour sliding lifetime; replay revokes the token family. This is not an absolute session lifetime limit.

Management endpoints are described in [`contracts/openapi/identity.yaml`](../../contracts/openapi/identity.yaml). The current permission catalog contains Identity management permissions; Ecommerce business permissions and business endpoints are not seeded here.
