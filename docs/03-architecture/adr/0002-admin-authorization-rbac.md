# ADR-0002: Use RBAC with Fine-Grained Permissions for Admin APIs

- **Status:** Accepted
- **Date:** 2026-11-03
- **Scope:** M1 Backoffice administrator authorization across Identity and Ecommerce
- **Implementation:** Not started; tracked by [Issue #11](https://github.com/XdpsX/ecommerce/issues/11)

## Context

The single-shop Backoffice needs administrator authentication and access control before Ecommerce business APIs can be exposed. A simple `ADMIN` flag is insufficient as responsibilities become more specialized. At the same time, tenant-specific or context-dependent authorization is not an M1 requirement. Identity is a separate service and Ecommerce must authorize requests independently of the BFF, consistent with [ADR-0001](0001-backoffice-bff-as-api-gateway.md).

## Decision

1. Use **role-based access control (RBAC)**. Identity owns administrator accounts, roles, permissions and assignments; a role groups permissions.
2. Authorize protected Ecommerce operations against **fine-grained effective permissions** using a stable `<resource>:<action>` naming convention, introduced incrementally with actual APIs.
3. Identity-issued JWT access tokens carry standard identity/audience/time claims and `scope`, `roles` and effective `permissions`. Ecommerce independently validates the token and enforces required permissions. Scopes and administrator permissions have distinct semantics.
4. Provision the initial privileged administrator using a secure, explicit one-time CLI bootstrap; do not seed a production default password on every startup.
5. Keep security data in Identity-owned persistence; Ecommerce owns business data and stores only stable administrator ID references where necessary.
6. **Defer ABAC**. Re-evaluate contextual/attribute-based policies if requirements emerge for resource ownership, organization/shop tenancy, conditional approvals or similarly contextual decisions. Do not add an ABAC engine speculatively.

## Alternatives considered

- **Fixed role checks only:** simplest initially but leads to coarse-grained access and widespread role-name coupling as the API surface grows.
- **RBAC with permissions (chosen):** explicit, testable and sufficiently extensible for the current single-shop admin scope; adds a permission catalog and role-assignment management.
- **ABAC/policy engine now:** supports complex resource/context rules but adds policy modeling, attribute retrieval, operational and audit complexity without current requirements.
- **Remote permission lookup on every request:** can reflect changes immediately but introduces latency, Identity availability coupling and caching/failure-mode concerns. Local JWT authorization is preferred for the initial design.

## Consequences and constraints

- Token permissions represent a **snapshot** at issuance. Permission removal is not immediately effective for already-issued tokens without a revocation/short-lived-token strategy. Finalize token TTL, refresh and revocation behavior before deployment.
- Role changes and permission grants must be controlled and auditable. Fail closed when required claims or permissions are absent.
- Identity remains the source of truth for entitlements; Ecommerce must not query or mutate Identity database tables directly.
- API-level permission enforcement does not remove the need for domain-specific authorization checks where a future operation depends on ownership or business context.
- This ADR approves the authorization model, **not** a complete role catalog, token lifetime, specific endpoints or the BFF implementation.

## Related documentation

- [Identity Service design](../identity-service.md)
- [ADR-0001 — Backoffice BFF](0001-backoffice-bff-as-api-gateway.md)
- [Issue #11](https://github.com/XdpsX/ecommerce/issues/11)
