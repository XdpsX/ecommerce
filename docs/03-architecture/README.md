# Architecture: current scope and direction

## Current repository state

The monorepo contains four independent Spring Boot **application skeletons** under `services/`: `ecommerce`, `identity`, `notification` and `media`. `web/backoffice` is a React frontend skeleton. `web/storefront` and both BFF directories are placeholders. No business modules from the legacy repositories have been migrated into these skeletons.

`contracts/openapi/` and `contracts/events/` are reserved for future contracts. The local Compose files under `infra/compose/` are currently empty placeholders, not a working environment.

## Target direction (not implemented)

The intended direction is an Ecommerce modular monolith with separate Identity, Notification and Media capabilities, Backoffice and Storefront frontends, and BFFs only when justified by actual integration needs. This describes an aspiration, not a confirmed deployed topology.

Service ownership, authentication flows, event and HTTP contracts, data boundaries and deployment architecture must be established from approved decisions and implementation evidence. Use `$architecture-review` for material trade-offs; add ADRs only when approved.

## Legacy references

- [Ecommerce backend](https://github.com/XdpsX/ecommerce-be)
- [Backoffice](https://github.com/XdpsX/ecommerce-backoffice)
- [Identity](https://github.com/XdpsX/practical-identity)
- [Notification](https://github.com/XdpsX/notification-service)

See [documentation governance](../06-development/documentation.md) for source-of-truth rules.
