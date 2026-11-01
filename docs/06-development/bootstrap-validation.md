# Bootstrap validation

## Status

Static repository audit completed; executable validation remains pending. GitHub repository reads do not execute Maven, pnpm, or browser tests.

## Backend

Requires Java 25. For each of `ecommerce`, `identity`, `notification`, `media`:

```sh
cd services/<application>
./mvnw clean verify
```

On Windows use `mvnw.cmd clean verify`. Check Maven Wrapper downloads and Spring Boot 4.1.1 resolution.

## Backoffice

Requires compatible Node.js and Corepack/pnpm (version pinned in `package.json`).

```sh
cd web/backoffice
corepack pnpm install --frozen-lockfile
corepack pnpm lint
corepack pnpm format:check
corepack pnpm build
corepack pnpm test
```

Orval generation is deliberately **not** part of bootstrap checks: its target OpenAPI endpoint and custom mutator require a running API and `src/api/client.ts`, which will be introduced during API integration. E2E tests likewise require an explicit browser/test setup.

## Acceptance

- All four backend builds/tests pass.
- Backoffice install, lint, formatting check, build and tests pass.
- Document any failing command and fix it before marking CR-01 complete.
- Do not migrate domain code in this CR.
