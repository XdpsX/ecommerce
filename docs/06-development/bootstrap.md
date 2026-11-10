# Bootstrap and developer verification

This document describes the **current monorepo skeleton** and repeatable developer commands. Historical CR acceptance results belong in the corresponding issue, PR or CI run, not in this guide.

## Prerequisites

- JDK 25 for the four backend services
- Node.js compatible with `web/backoffice/package.json` and its tooling (CI uses Node 24)
- pnpm 12.9.1 for Backoffice (declared in `package.json`)

## Backend

Each service has its own Maven Wrapper and can be verified independently. From the repository root:

```sh
cd services/ecommerce
./mvnw -B --no-transfer-progress verify
```

Repeat in `services/identity`, `services/notification` and `services/media`. On Windows use `mvnw.cmd` instead of `./mvnw`. Maven `verify` includes the configured Spotless check.

Windows users can also use the [multi-service Maven runner](../../tooling/scripts/README.md).

## Backoffice

```sh
cd web/backoffice
corepack pnpm install --frozen-lockfile
corepack pnpm format:check
corepack pnpm lint
corepack pnpm test
corepack pnpm build
corepack pnpm dev
```

The `dev` command starts Vite on port 3000 and proxies browser API/OAuth paths to BFF port 8081. Configure Identity, Redis and the confidential client using the [BFF guide](../../services/backoffice-bff/README.md). `generate:api` consumes the checked-in browser OpenAPI contract without a running backend; generation drift is checked in CI. See the [Backoffice README](../../web/backoffice/README.md).

## CI

Six application-specific workflows live under `.github/workflows/`: `ecommerce-ci.yml`, `identity-ci.yml`, `notification-ci.yml`, `media-ci.yml`, `backoffice-ci.yml` and `backoffice-bff-ci.yml`. They run application-specific verification on configured push/PR path filters and support manual dispatch. BFF CI builds Identity before testing real OAuth and Redis integration. Check the workflow files for exact triggers and commands.

A workflow definition does not prove a successful run. Consult GitHub Actions for actual status. Path-filtered workflows may be skipped on docs-only changes; consider this when configuring required checks.

## Current limitations

- Business modules have not been migrated.
- Storefront and its BFF remain placeholders; Backoffice BFF is implemented.
- PostgreSQL and Redis are provisioned with Docker Compose; see [local infrastructure](../../infra/README.md). Application containers have not yet been added.
- OpenAPI contracts are checked in; event contracts remain reserved.

Follow [workflow](workflow.md) and [conventions](conventions.md). Agent instructions are in [AGENTS.md](../../AGENTS.md); optional GitNexus/Serena setup is in [tooling](../../tooling/code-intelligence.md).
