# Backoffice

React / TypeScript / Vite application skeleton, based on tooling from [XdpsX/ecommerce-backoffice](https://github.com/XdpsX/ecommerce-backoffice). Business screens have not been migrated.

## Local development

Use a compatible Node.js version (CI uses Node 24) and pnpm 12.9.1, as declared in `package.json`.

```sh
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Run the following commands from `web/backoffice/`:

| Command | Purpose |
| --- | --- |
| `corepack pnpm format:check` | Check Prettier formatting |
| `corepack pnpm lint` | Run ESLint |
| `corepack pnpm test` | Run Vitest unit tests |
| `corepack pnpm build` | TypeScript and Vite production build |
| `corepack pnpm mock` | Start Vite in mock mode |
| `corepack pnpm test:e2e` | Run Playwright (requires browser/test setup) |
| `corepack pnpm generate:api` | Run Orval (requires an available OpenAPI source and client integration) |

The API client and generated contracts are not yet integrated as part of this bootstrap skeleton; do not assume the Orval command works without additional setup. The root [bootstrap guide](../../docs/06-development/bootstrap.md) documents CI verification and backend setup.
