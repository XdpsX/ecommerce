# Identity Service — Admin Authentication and Authorization

> **Status:** Approved architecture for [Issue #11](https://github.com/XdpsX/ecommerce/issues/11); core Identity and Ecommerce security implementation is present. Deployment and BFF integration remain separate work.
> **Scope:** M1/P1 single-shop Backoffice administrators, Identity Service and Ecommerce resource-server integration.
> **Related decisions:** [ADR-0001](adr/0001-backoffice-bff-as-api-gateway.md), [ADR-0002](adr/0002-admin-authorization-rbac.md).

## Context and boundaries

Identity and Ecommerce now implement the authentication producer and resource-server consumer described here. The Backoffice BFF remains a placeholder and is outside Issue #11. This document describes approved behavior; source and service READMEs record implementation and operating details.

Identity is the OAuth2/OIDC authorization server and the authority for administrator accounts and security entitlements. The future Backoffice BFF is the confidential OAuth2/OIDC client, using Authorization Code flow (with PKCE where supported), server-side Redis sessions and token relay, as established by ADR-0001. Ecommerce is an independent OAuth2 Resource Server: it must never trust the BFF alone for access control. Browser cookies, CSRF, routing, and session management belong to the separate BFF implementation.

Customer-facing authentication, marketplace sellers/roles, Storefront BFF, and BFF implementation are outside Issue #11.

## Admin authentication lifecycle

- Store administrator accounts and password hashes in Identity-owned PostgreSQL tables. Never store plaintext credentials or log credentials, tokens or secrets.
- Implement secure administrator authentication through the authorization server's interactive login flow and standard OAuth2/OIDC endpoints. The initial scope covers enabled/disabled account status, login and logout interoperability, and the authorization data needed to issue tokens.
- Publish issuer discovery metadata and a JWKS endpoint; protect signing private keys and support a documented rotation procedure.
- Persist OAuth2 client registrations and authorization/consent state as needed by the selected server implementation; use versioned migrations for persistent schema changes.
- Define a registered confidential Backoffice BFF client for integration, without implementing that BFF in this issue. Redirect URIs, secrets and allowed grant types must be environment-specific and validated.
- Prefer standards-compliant authorization code exchange; do not introduce a browser-facing password grant or expose access/refresh tokens to browser JavaScript.

Account creation, enable/disable operations and entitlement assignment require administrative authorization. Public self-registration, customer login, password recovery and MFA are **not automatically included** in Issue #11; they require explicit scope decisions if needed for its acceptance criteria.

## Access-token contract

Issue #11 adopts JWT access tokens. The initial contract includes:

| Claim | Meaning | Validation / use |
| --- | --- | --- |
| `iss` | Identity issuer URL | Must match configured trusted issuer |
| `sub` | Stable opaque administrator ID | Identity key, never email or mutable username |
| `aud` | Intended resource-server audience | Ecommerce requires its configured audience |
| `iat` / `exp` | Issued-at / expiry timestamps | Reject expired or otherwise invalid tokens |
| `scope` | OAuth2 delegated scopes | Space-delimited standard OAuth2 scope string |
| `roles` | Assigned role identifiers | String array, e.g. `["SUPER_ADMIN"]` |
| `permissions` | Effective permission identifiers | String array, e.g. `["catalog:read"]` |

JWT example (illustrative; not a live token):

```json
{
  "iss": "https://identity.example.com",
  "sub": "b6a3f2d7-2fd7-48db-a2c9-f5b8314d9371",
  "aud": ["ecommerce-api"],
  "iat": 1793700000,
  "exp": 1793700600,
  "scope": "openid profile",
  "roles": ["CATALOG_MANAGER"],
  "permissions": ["catalog:read", "catalog:write"]
}
```

`scope` expresses OAuth2 client/delegation scope; `permissions` expresses administrator authorization. They must not be conflated. Identity resolves effective permissions from assigned roles when issuing a token. Ecommerce validates signature via JWKS, issuer, audience, expiry and relevant token constraints, then maps `permissions` to authorities and checks permissions on protected operations. Unrecognized or missing required permissions fail closed. Unauthenticated requests receive 401; authenticated requests lacking authorization receive 403.

Access tokens use `ecommerce-api` and `identity-api` audiences, RS256 signatures, a five-minute lifetime, and permissions filtered to the audiences requested by the client. Refresh tokens rotate, have an eight-hour sliding lifetime, and are tracked by Identity-owned token families for replay detection. Ecommerce allows at most 30 seconds of clock skew. Keys are supplied as an external JWK set with an explicit active `kid`; rotation and backup requirements are documented in the Identity README. JWT permissions are snapshots: changes to roles do not automatically invalidate already-issued tokens.

## Authorization model

Use role-based access control (RBAC) with roles grouping fine-grained permissions, per [ADR-0002](adr/0002-admin-authorization-rbac.md).

- Permission identifiers follow `<resource>:<action>` in lowercase, e.g. `catalog:read`, `catalog:write`, `order:read`. These are **illustrative**, not a promise that the corresponding APIs already exist.
- Roles are Identity-owned groupings of permissions. `SUPER_ADMIN` is the initial privileged bootstrap role; additional roles should be introduced only when actual endpoints and administrative workflows require them.
- Protect operations using effective permissions, not a broad `ADMIN` role check alone. Role names may be useful for administration and diagnostics, but are not the sole enforcement mechanism.
- Do not introduce tenant/shop attributes or a generic ABAC policy engine now. A later move toward ABAC can layer contextual policies on top of stable identity and permission identifiers without making token roles the only authorization contract.

## Initial administrator bootstrap

Production bootstrap is an explicit, one-time administrative CLI/command, not an automatically re-run startup seed.

1. An operator invokes the bootstrap command with securely provided credentials (interactive prompt or approved secret input; never committed or printed).
2. The command checks whether the initial administrator has already been provisioned, validates inputs, hashes the password with an appropriate adaptive password hashing algorithm, and creates an enabled administrator with `SUPER_ADMIN`.
3. Repeated invocation must be safe: do not overwrite passwords or silently elevate an existing account. Fail with a clear non-sensitive result when bootstrap is already complete.
4. Restrict invocation to trusted operators and record a non-sensitive audit event.

A development/test-only opt-in seed is permissible with isolated configuration and no production default credentials. The precise command name, credential delivery mechanism, and bootstrap completion marker are implementation details to document when built.

## Data ownership

| Owner | Data |
| --- | --- |
| Identity | Administrator identity IDs, usernames/emails and minimal security profile, credential hashes, account state, roles, permissions, role assignments, OAuth2 clients/authorizations/consents, signing-key references and security audit records |
| Ecommerce | Products, catalog, inventory, orders and other business-domain data; stable administrator ID references for actor/audit fields when needed |
| Backoffice BFF (separate issue) | Browser-facing session state in Redis; OAuth2 client session/token management |

Each service owns its persistence schema and migrations. No direct cross-service table reads or shared mutable administrator database. Ecommerce relies on validated token claims for request-time authorization and may store `sub` as a stable actor reference; Identity is the source of truth for current roles and permissions.

## Contract and implementation responsibilities

- **Identity producer:** OAuth2/OIDC discovery, authorization/token/userinfo endpoints as applicable, JWKS, JWT claim semantics, administrator management and bootstrap behavior.
- **Ecommerce consumer:** trusted issuer/JWKS configuration, audience validation, claim-to-authority mapping, permission checks and consistent 401/403 behavior.
- **BFF future consumer:** confidential-client registration, redirect URIs, OIDC login and TokenRelay interoperability, per ADR-0001.
- The implemented administrator management contract is in [`contracts/openapi/identity.yaml`](../../contracts/openapi/identity.yaml). The Ecommerce production application still has no business API; its test-only security probe exercises the resource-server boundary without publishing a fictitious business endpoint.

## Verification expectations

Test successful and failed administrator authentication, disabled-account behavior, password storage, bootstrap idempotency, role-to-permission resolution and token issuance. Exercise resource-server verification of signature/issuer/audience/expiry, missing token (401), insufficient permission (403), allowed permission (success) and relevant persistence migrations using appropriate integration tests. Tests should use generated test keys and credentials, never production secrets.

## Deferred design details

Before implementing the security-critical flows, finalize token lifetime and refresh/revocation strategy, exact management endpoints and admin lifecycle operations, production signing-key storage, registered client configuration and the initial permission catalog based on real protected APIs. ABAC remains a possible future evolution, not a requirement for Issue #11.

## References

- [ADR-0001 — Backoffice BFF](adr/0001-backoffice-bff-as-api-gateway.md)
- [ADR-0002 — Admin RBAC](adr/0002-admin-authorization-rbac.md)
- [Architecture overview](README.md)
- [Issue #11](https://github.com/XdpsX/ecommerce/issues/11)
- [Practical Identity reference](https://github.com/XdpsX/practical-identity)
