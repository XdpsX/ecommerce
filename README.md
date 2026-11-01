# Ecommerce Platform

A learning-oriented ecommerce platform, evolving from existing independent repositories into a monorepo. The target architecture combines a modular-monolith Ecommerce API with independently deployable supporting services and React frontends.

> **Status: repository bootstrap.** Application directories contain skeletons only. Domain/business code has **not** been migrated.

## Layout

- `backend/ecommerce-api` — Java 25 / Spring Boot 4 skeleton; future ecommerce modular monolith.
- `backend/identity-service` — Identity skeleton; future OAuth2/OIDC integration.
- `backend/notification-service`, `backend/media-service` — reserved for future independently initialized services.
- `backend/backoffice-bff`, `backend/storefront-bff` — reserved for BFFs; design not finalized.
- `web/backoffice`, `web/storefront` — React frontend workspaces (not yet migrated).
- `contracts` — API and event contracts when introduced.
- `docs` — documentation index and architecture/development guides.
- `infra` — infrastructure and local Compose skeletons.
- `tooling` — shared scripts and quality tooling when adopted.

## Getting started

See [bootstrap instructions](docs/06-development/bootstrap.md). Run a skeleton independently:

```sh
cd backend/ecommerce-api
./mvnw test
```

Requires Java 25. Windows: `mvnw.cmd test`. No application integrations are wired yet.

## Source repositories

- [Ecommerce backend](https://github.com/XdpsX/ecommerce-be)
- [Backoffice](https://github.com/XdpsX/ecommerce-backoffice)
- [Practical Identity](https://github.com/XdpsX/practical-identity)
- [Notification Service](https://github.com/XdpsX/notification-service)

Existing repositories remain the reference sources; migration will proceed **module by module** via issues and PRs.
