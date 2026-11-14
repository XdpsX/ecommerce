# Architecture: current scope and direction

## Current repository state

The monorepo has Ecommerce and Identity foundations, a Media image-storage service and the [Backoffice BFF](../../services/backoffice-bff/README.md); Notification remains a skeleton. Backoffice has a protected temporary dashboard with English/Vietnamese UI. Storefront and its BFF remain placeholders. Legacy business modules have not been migrated.

`infra/compose/compose.yml` provisions PostgreSQL and Redis for IDE-based development. `compose.local.yml` includes the infrastructure and is reserved for future containerized application services. See [local infrastructure](../../infra/README.md). OpenAPI specifications live in `contracts/openapi/`; events remain reserved for future schemas.

## Target direction and implemented foundations

The target is an Ecommerce modular monolith with separate Identity, Notification and Media capabilities, Backoffice and Storefront frontends, and an approved Backoffice BFF/API Gateway direction. Storefront BFF remains deferred.

[ADR-0001](adr/0001-backoffice-bff-as-api-gateway.md) approves Spring Cloud Gateway (WebFlux) as the Backoffice BFF, with OAuth2/OIDC confidential-client login and Redis-backed sessions. Issue #14 implements this browser boundary with no refresh-token support and local BFF logout; Identity SSO may remain active. The [browser contract](../../contracts/openapi/backoffice-bff.yaml) defines session, CSRF and logout. Business modules and production deployment remain future work.

## Backend package organization

All backend services use the approved separation of root `config` for Spring composition/properties, service-local `shared/security` and `shared/error` for service-wide technical behavior, and feature-owned `domain/application/adapter` packages. Create only packages with actual contents. `shared` is internal to each service, not a shared runtime library, and does not contain feature business exceptions or use cases. Ecommerce retains namespace `com.ecommerce.api`; other namespaces remain unchanged. Identity's OAuth2 capability and Media's `asset` capability retain their owners. See the [code organization convention](../06-development/code-organization.md) for dependency rules.

Implementation status: service-wide HTTP error handling now lives in each service's `shared/error`; BFF browser filters and the locale-aware authorization-request resolver live in `shared/security`. Feature-owned HTTP adapters remain with their features, and root `config` retains composition. The service READMEs describe the current package boundaries.

## Identity design

[Identity Service — Admin Authentication and Authorization](identity-service.md) documents the Issue #11 implementation, including JWT claims, admin bootstrap and security data ownership. [ADR-0002](adr/0002-admin-authorization-rbac.md) records the RBAC-with-permissions decision and deferred ABAC evaluation.

## Ecommerce Category persistence direction

Issue #23 implements Category persistence in Ecommerce with Spring Data JPA + Hibernate and Flyway-owned DDL. Category uses JPA `@Version`; hierarchy and order changes use an optimistic structure revision without an explicit global pessimistic lock. Create does not require a client structure revision; the backend retries bounded revision contention. See the [Category architecture](ecommerce-category.md), [OpenAPI contract](../../contracts/openapi/ecommerce.yaml) and [Ecommerce README](../../services/ecommerce/README.md). Identity V7 provides the required audience-scoped permissions. Identity and Media persistence technology are unchanged.

## Media design

[Media Service](media-service.md) documents the implemented private image-storage boundary, Identity-issued entitlements, authenticated provider assets, cleanup recovery and the approved [independent consumer image-reference policy](media-service.md#consumer-image-references). Ecommerce stores `imageAssetId`; Media may delete assets independently of those references, and consumers display placeholders for unavailable images. Attachment reservation, reference tracking, deletion protection and Media Library UI are outside Issue #23. Asset code remains feature-owned; the backend package convention above defines the placement of service-wide technical components. Deployed-account provider smoke checks remain release requirements. [ADR-0003](adr/0003-media-direct-upload-cloudinary-a1.md) selects the A1 direct-upload direction; its [security and operations specification](media-direct-upload-security-operations.md) preserves failed/unresolved gates and policy decisions after spike closure.

## Organization

- [ADRs](adr/README.md): Architectural decisions, alternatives and consequences; accepted does not mean implemented.
- [Architecture diagrams](diagrams/README.md): Structural context, service/component relationships and deployment/network topology.
- [Flows](../05-flows/README.md): Temporal sequences, business processes and state transitions; link to ADRs instead of duplicating decisions.
- [Contract conventions](../04-contracts/README.md): Human-readable policies; machine-readable specifications live in root `contracts/`.

Use `$architecture-review` for material trade-offs; add ADRs only when approved. See [documentation governance](../06-development/documentation.md).

## References

- [Ecommerce backend](https://github.com/XdpsX/ecommerce-be)
- [Backoffice](https://github.com/XdpsX/ecommerce-backoffice)
- [Identity](https://github.com/XdpsX/practical-identity)
- [Notification](https://github.com/XdpsX/notification-service)
