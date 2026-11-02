# Ecommerce Platform

Ecommerce monorepo in the **platform foundation / bootstrap** stage. Application skeletons and tooling are present; business modules have not been migrated into this repository.

## Repository layout

| Path | Purpose | Current state |
| --- | --- | --- |
| `services/ecommerce/` | Ecommerce backend | Spring Boot skeleton |
| `services/identity/` | Identity service | Spring Boot skeleton |
| `services/notification/` | Notification service | Spring Boot skeleton |
| `services/media/` | Media service | Spring Boot skeleton |
| `services/backoffice-bff/`, `services/storefront-bff/` | Reserved BFF locations | Placeholders |
| `web/backoffice/` | React admin application | Frontend skeleton |
| `web/storefront/` | Storefront | Placeholder |
| `contracts/` | API and event contracts | Reserved directories |
| `infra/compose/` | Local infrastructure configuration | Placeholder files; not a runnable stack |
| `.agents/skills/` | Agent workflows and optional UI tooling | Skills and references |
| `docs/` | Durable project documentation | Development and architecture guides |

Backend baseline: Java 25 and Spring Boot 4.1.1. Backoffice baseline: React, TypeScript, Vite and pnpm; consult each application's configuration for exact versions.

## Getting started

See [bootstrap and verification](docs/06-development/bootstrap.md) for application-specific commands. For the Windows multi-service Maven runner, see [tooling/scripts](tooling/scripts/README.md).

## Documentation and workflow

- [Documentation index](docs/README.md)
- [Product roadmap (planned, not implementation status)](docs/01-product/roadmap.md)
- [Change request workflow](docs/06-development/workflow.md)
- [Agent skills](docs/06-development/agent-skills.md)
- [Documentation governance](docs/06-development/documentation.md)
- [Architecture scope](docs/03-architecture/README.md)

## Reference repositories

These repositories are **references only**, not migration targets or required implementations. We may redesign business rules, architecture, APIs, persistence and UI/UX rather than copying existing code or screens. The [product roadmap](docs/01-product/roadmap.md) defines the single-shop-first scope.

- **Legacy Ecommerce:** [Ecommerce backend](https://github.com/XdpsX/ecommerce-be), [Backoffice](https://github.com/XdpsX/ecommerce-backoffice) — discover existing modules and business capabilities.
- **Service references:** [Identity](https://github.com/XdpsX/practical-identity), [Notification](https://github.com/XdpsX/notification-service) — compare service designs and practices.
- **External references:** [YAS](https://github.com/nashtech-garage/yas), [Hospital Management System](https://github.com/qwan30/hospital-management-system) — selectively study architecture, organization and engineering practices, even across domains.

Reference repositories are not evidence that their features exist in this monorepo.
