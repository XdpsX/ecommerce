# Architecture: current scope and direction

## Current repository state

The monorepo has four independent Spring Boot application skeletons: `ecommerce`, `identity`, `notification` and `media`. `web/backoffice` is a React skeleton; `web/storefront` and both BFF directories are placeholders. Legacy business modules have not been migrated.

`infra/compose/compose.yml` provisions PostgreSQL and Redis for IDE-based development. `compose.local.yml` includes the infrastructure and is reserved for future containerized application services. See [local infrastructure](../../infra/README.md). `contracts/openapi/` and `contracts/events/` are placeholders for future schemas.

## Target direction (not implemented)

The target is an Ecommerce modular monolith with separate Identity, Notification and Media capabilities, Backoffice and Storefront frontends, and an approved Backoffice BFF/API Gateway direction. Storefront BFF remains deferred.

[ADR-0001](adr/0001-backoffice-bff-as-api-gateway.md) approves Spring Cloud Gateway (WebFlux) as the Backoffice BFF, with OAuth2/OIDC confidential-client login and Redis-backed sessions. The BFF is **not implemented**. Other ownership, HTTP/event contracts, data boundaries and deployment decisions need further evidence.

## Identity design

[Identity Service — Admin Authentication and Authorization](identity-service.md) documents the approved design direction for Issue #11, including JWT claims, admin bootstrap and security data ownership. [ADR-0002](adr/0002-admin-authorization-rbac.md) records the RBAC-with-permissions decision and deferred ABAC evaluation. These designs are not yet implemented.

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
