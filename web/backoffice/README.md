# Backoffice

React / TypeScript / Vite Backoffice foundation, based on tooling from [XdpsX/ecommerce-backoffice](https://github.com/XdpsX/ecommerce-backoffice). It includes a responsive application shell, temporary dashboard, informational sign-in entry, code-based routing, and separate query/router providers. English and Vietnamese translations are organized by shared and feature ownership, the selected language is remembered in browser storage, and missing translations fall back to English. Query and Router Devtools are available only in development. Active session guards and browser authentication remain out of scope until the G2 BFF browser contract is approved.

## Local development

Use a compatible Node.js version (CI uses Node 24) and pnpm 12.9.1, as declared in `package.json`.

```sh
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Run the following commands from `web/backoffice/`:

| Command                      | Purpose                                                                 |
| ---------------------------- | ----------------------------------------------------------------------- |
| `corepack pnpm format:check` | Check Prettier formatting                                               |
| `corepack pnpm lint`         | Run ESLint                                                              |
| `corepack pnpm test`         | Run Vitest unit tests                                                   |
| `corepack pnpm build`        | TypeScript and Vite production build                                    |
| `corepack pnpm mock`         | Start Vite in mock mode                                                 |
| `corepack pnpm test:e2e`     | Run Playwright (requires browser/test setup)                            |
| `corepack pnpm generate:api` | Run Orval (requires an available OpenAPI source and client integration) |

The `/` dashboard is temporary and contains no fabricated business metrics or API calls. Translation resources live at `src/shared/locales/{en,vi}/common.json` and `src/features/{auth,home}/locales/{en,vi}.json`; `src/app/i18n.ts` composes the `common`, `auth` and `home` namespaces. The language preference is stored as `backoffice.language`, unsupported preferences fall back to English, and switching language updates `html.lang`.

No BFF endpoints, session DTOs, API client or generated contracts are integrated yet. Route guards, session states and return-path navigation await the approved G2 browser contract; loading and unavailable states must remain distinct from unauthenticated when that integration is implemented. The sign-in entry is informational and does not simulate authentication. The Orval command is not expected to work until the checked-in BFF contract is available. The root [bootstrap guide](../../docs/06-development/bootstrap.md) documents CI verification and backend setup.
