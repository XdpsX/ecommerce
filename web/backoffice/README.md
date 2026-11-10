# Backoffice

React/TypeScript/Vite admin foundation with a temporary protected Dashboard, responsive shell, English/Vietnamese JSON namespaces and development-only TanStack tools. Browser authentication uses the [Backoffice BFF](../../services/backoffice-bff/README.md), following ADR-0001. No business metrics or business screens are implemented.

## Local development

Use Node 24 and pnpm 12.9.1 (package.json/CI). From `web/backoffice`:

```sh
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

If the machine's Corepack cache is broken, the equivalent project-scoped fallback is `npm exec --yes --package=pnpm@12.9.1 -- pnpm <command>`, without changing the global toolchain.

Vite serves `http://localhost:3000` and proxies `/bff`, `/oauth2`, `/login/oauth2` and `/api` to BFF `http://localhost:8081` (`BFF_UPSTREAM` may override the dev target). Keep the browser origin/callback on localhost:3000. Start Identity and BFF using the [BFF setup guide](../../services/backoffice-bff/README.md); Identity needs PostgreSQL, signing keys, a provisioned administrator and a registered confidential client; BFF needs Redis and the matching client secret. Do not put secrets in VITE variables. A missing backend shows a retryable session-unavailable page; it does not simulate login.

## Browser behavior and ownership

`app/config` holds configuration, `app/providers/AppProvider` composes Query, feature Session and Router providers, `app/router` registers routes, `app/guards` contains RequireAuth/RequireGuest, and `app/layout/components` owns header/navigation/language controls. QueryClient/router instances are stable for each provider lifetime.

The Dashboard `/` requires an authenticated BFF session; `/login` is guest-only. Loading and unavailable states hide protected content without prematurely redirecting to login. Sign-in navigates to Identity through the BFF using the selected `ui_locales=en|vi`; Identity uses that hint for its localized login page. The frontend never handles passwords or OAuth tokens. Return paths are validated on both browser/server boundaries, and generated login links omit the default `returnTo=/` while preserving safe non-default paths. Session queries revalidate on focus/entry, and downstream 401 requests session reconciliation; permission 403 does not log the user out. CSRF is fetched before unsafe requests, which are never automatically replayed. Request cancellation and a session generation prevent old responses/errors repopulating a later identity's cache.

Logout hides sensitive content while pending, invalidates the local BFF session on success and reconciles uncertain failures with a fresh session check. **Identity SSO may remain active**, so subsequent sign-in can be automatic. There is no refresh-token support: the BFF rejects session/API use at the earlier of the token response expiry and Identity JWT exp, with no extra clock skew. The 30-minute Redis idle limit never extends token lifetime. Browser code does not inspect JWTs or refresh them.

Shared translations live in `src/shared/locales/{en,vi}/common.json`; features own `features/{auth,home}/locales/{en,vi}.json`. `app/i18n.ts` composes namespaces, persists `backoffice.language`, falls back to English and updates `html.lang`. Error codes remain machine-readable. Devtools run only in development, excluding test mode.

## UI primitives and visual tokens

Backoffice uses the checked-in shadcn Base UI primitives in `src/shared/components/ui` for buttons, language selection, alerts and the mobile navigation sheet. `src/index.css` owns the light semantic theme and is the reference for background, text, primary green, borders, focus ring and radius. Identity keeps its independent Thymeleaf/CSS rendering; its small matching token set and self-hosted Geist font assets live under `services/identity/src/main/resources/static/login-assets`. Keep these values aligned when either surface changes; Identity does not depend on the Backoffice build. See the [Identity login asset notes](../../services/identity/README.md#login-presentation).

## Contract and verification

The checked-in [browser OpenAPI](../../contracts/openapi/backoffice-bff.yaml) is the source for Orval; its only external reference is the shared local Problem schema. `generate:api` requires no running backend and formats its output deterministically under `src/shared/api/generated`. Do not hand-edit generated files or substitute a live OpenAPI URL. The shared Axios mutator owns same-origin credentials, CSRF, cancellation and REST error normalization; auth owns session transitions/cache isolation.

Run from `web/backoffice`:

| Command                      | Purpose                                                   |
| ---------------------------- | --------------------------------------------------------- |
| `corepack pnpm generate:api` | Generate typed client/hooks; repeat and inspect for drift |
| `corepack pnpm format:check` | Check Prettier formatting                                 |
| `corepack pnpm lint`         | ESLint                                                    |
| `corepack pnpm test`         | Vitest/RTL/MSW session, guards, i18n and API boundaries   |
| `corepack pnpm build`        | TypeScript and Vite production bundle                     |

Ordinary FE integration tests mock HTTP and require no backend. BFF Maven IT verifies actual Identity, Redis persistence/restart, CSRF, JWT relay/security and expiry separately. Actual browser verification covers callback/proxy/cookies, protected navigation and local logout; report this separately from mocked tests. Formal E2E suites belong in the separate E2E repository. `test:e2e` is legacy scaffolding, not evidence of an integrated suite; `mock` does not enable mock authentication. CI also regenerates the browser client and checks drift.
