# Repository bootstrap and migration

## Current state

This repository initially contained `services/ecommerce`, `services/identity`, `web/storefront` and `infra/compose`. Bootstrap standardizes the requested root layout to `backend/` and `web/` without importing legacy domain logic.

## Prerequisites

- Git, JDK 25, Maven Wrapper, Node.js and pnpm for later frontend initialization.
- Docker/Compose only when infrastructure dependencies are actually configured.

## Verify backend skeletons

```sh
cd backend/ecommerce-api && ./mvnw test
cd ../identity-service && ./mvnw test
```

These are commands to run locally; CI and runtime verification are not yet claimed. On Windows use `mvnw.cmd`.

## Remaining bootstrap work

1. Inspect source repository build files and initialize the notification and media service skeletons when their technology/version decisions are confirmed.
2. Initialize `web/backoffice` and `web/storefront` from the source repositories' tooling configurations, **without** copying application features.
3. Fix any skeleton package/artifact names and tests; configure local environment and isolated CI checks.
4. Add contracts only when a concrete integration requires them.
5. Evaluate AI tools and skills in a separate CR after baseline builds pass.

## Incremental migration

- Preserve legacy repositories as read-only references during migration.
- Create a GitHub issue per domain module or bounded integration slice.
- Record source paths, target paths, dependencies, acceptance criteria, tests, API/events and docs impact.
- Migrate one module at a time, starting with a low-coupling catalog module (e.g. Brand/Category), then Product and subsequent domains as dependency analysis permits.
- Run targeted tests, perform independent review and merge a small PR. Avoid a one-shot code copy.
