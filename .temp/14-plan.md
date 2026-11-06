# Issue #14 — Backoffice foundation plan

Status: Proposed; not approved for implementation. User approved separating BFF into its own CR; browser contract details remain pending.
Issue: https://github.com/XdpsX/ecommerce/issues/14

## Recommended scope

Implement the smallest usable Backoffice foundation in `web/backoffice`: accessible shared shell, navigation, login entry/logout, protected landing route, typed session/API transport and consistent error states. Keep the accepted BFF architecture. Recommend delivering the missing BFF as a separate dependency CR, agreeing its browser contract before frontend implementation and integrating it before claiming the complete login acceptance criterion.

Use `phase/1-core-services-foundation` as integration/PR base; the issue's `phase/1` is outdated. Inspection occurred on this integration branch at `52e525275498735d72da411ca12fcfa177c7286e` (PR #18 integrated). Working tree has pre-existing untracked `.temp/`; preserve it. No branch creation/switch or application changes are authorized by this plan.

Out of scope: catalog/order/admin-management screens, analytics, Media workflows, Storefront, browser OAuth token handling, unrelated backend refactoring, shared cross-application UI packages and speculative infrastructure.

## Current evidence

| Evidence | Implication |
| --- | --- |
| `web/backoffice/src/App.tsx`, `main.tsx`, `index.css`, `App.test.tsx` | Static bootstrap page, one smoke test, no router/providers/design tokens. |
| `web/backoffice/package.json`, `vite.config.ts` | TanStack Router/Query, Axios, Base UI, Tailwind, Vitest/RTL/MSW already available; Vite port 3000, no BFF proxy configured. No dependency upgrade needed for planning. |
| `web/backoffice/orval.config.ts` | Defaults to an unestablished localhost OpenAPI endpoint; references missing `src/api/client.ts`. Do not treat current generation as integrated. |
| `services/backoffice-bff/.gitkeep` | BFF has no implementation or browser session contract. |
| `docs/03-architecture/adr/0001-backoffice-bff-as-api-gateway.md` | Accepted WebFlux Spring Cloud Gateway confidential client, Redis sessions, HttpOnly cookie, CSRF, server-owned OAuth tokens. |
| `services/identity/.../authorization/adapter/oauth2/AuthorizationServerConfiguration.java` | Identity supports OIDC/login; UserInfo mapper returns only `sub`. Its `/api/**` routes require bearer tokens; Identity login cookie cannot authenticate them. |
| `services/identity/.../token/AccessTokenCustomizer.java`, `.../client/ClientRegistrationService.java` | Roles/permissions are access-token claims, not a guaranteed browser session DTO; confidential client requires PKCE S256, explicit callback allowlists; refresh grant defaults off. Paths abbreviated from `src/main/java/com/ecommerce/identity`. |
| `services/ecommerce/README.md`, resource-server configuration | No production business API; unknown routes denied. Test-only security probes must remain test-only. |
| `contracts/openapi/common/problem.yaml`, `docs/04-contracts/http-errors.md` | Shared Problem Details schema and safe status/code fallback already specified. |
| `.github/workflows/backoffice-ci.yml`, `docs/06-development/testing.md` | Existing FE CI runs format/lint/Vitest/build; FE integration tests mock HTTP. E2E journeys belong in a separate repository. |

## Ownership and proposed browser contract

These are proposals for agreement with the BFF CR, not existing endpoints. Do not independently implement competing contracts.

| Boundary | Owner / proposed behavior |
| --- | --- |
| Browser → BFF | Same-origin relative URLs through production ingress; Vite proxies matching paths locally. Cookie credentials, no browser Authorization header or client secret. |
| Session read | Proposed `GET /bff/session`: 200 with minimal authenticated principal `{subject}`; 401 JSON when unauthenticated. `Cache-Control: no-store`. Do not add username/permissions unless their trusted source and freshness are agreed. |
| Login | Proposed top-level navigation to `/oauth2/authorization/identity`; BFF owns code+PKCE exchange/callback. Return only to validated local app paths, excluding protocol-relative/external URLs. Identity hosts credential form; no frontend password grant/form. |
| CSRF | Proposed `GET /bff/csrf`: token plus agreed header name; BFF validates unsafe requests including logout. Acquire on bootstrap/as needed, refresh after session change; tokens are distinct from OAuth credentials. |
| Logout | Proposed CSRF-protected `POST /bff/logout`: invalidate Redis session and authorized-client/token state. Exact response and Identity end-session redirect must be agreed; FE must distinguish failed logout from confirmed completion. |
| Backend API routing | BFF owns explicit route allowlists and server-side token relay. Preserve downstream status, Problem Details and relevant headers. Do not add a production Ecommerce probe to demonstrate connectivity. |
| Identity → BFF | Identity owns credentials, entitlements, OIDC grants and signed tokens. BFF owns browser session and OAuth client state. Browser receives no raw tokens. |

Session and API XHR endpoints must return statuses/JSON rather than redirecting fetch requests to an HTML login page. UI navigation can redirect interactively. BFF cookies require Secure/HttpOnly/scoped SameSite in deployment; document explicit localhost development settings separately. Do not solve local integration by broadly enabling CORS on resource servers.

## Ordered implementation

1. Keep BFF implementation in a separate CR, as approved by the user. Freeze a minimal session/login/logout/CSRF contract, local origins, callback/post-logout allowlists and session lifetime/expiry behavior with that CR. Track the dependency before implementation; do not open an issue without authorization. Full login acceptance remains blocked until BFF integration exists.
2. Compose TanStack Query and Router under `src/app`; use code-based routes to avoid unnecessary generation setup. Add public login, protected `/` landing, not-found and route error behavior. Session states: loading, authenticated, unauthenticated, unavailable. Network/5xx errors provide retry rather than pretending the user logged out. Validate any return path.
3. Add `src/shared/api` transport and typed Problem Details normalization. Use agreed same-origin browser contract and CSRF for unsafe requests. Distinguish 401, 403, structured validation, unknown code, non-JSON/malformed error, network failure and cancellation. Never parse `detail` for decisions, render untrusted HTML or log credentials. Do not blindly retry unsafe operations.
4. Add `src/features/auth` session/login/logout workflow integrated with route guards. Coordinate identity-sensitive Query cache cancellation/clearing and prevent late requests restoring previous-session data. Avoid infinite 401 retries/redirect loops. A downstream 401 requires session revalidation; a 403 is forbidden, not evidence the session expired. Server authorization stays authoritative.
5. Build minimal design foundation: consistent color/type/spacing tokens, focused shared button/loading/error primitives, responsive navigation, header and main content. Use installed Base UI/Tailwind/icon/font tools only where needed. Provide visible focus, keyboard controls, semantic landmarks, skip link and accessible collapsed navigation. Landing contains a truthful welcome; no fabricated analytics, empty business links or role-management UI. Choose neutral styling and existing English copy as provisional defaults; no new i18n/theme framework work.
6. Align `components.json` aliases only for primitives actually added, following `shared/ui` convention. Update Vite proxy and test environment to the agreed same-origin contract. Configure Orval against a checked-in approved BFF spec, producing only consumed session/CSRF types/client under `src/shared/api/generated` with the shared mutator. Do not generate Identity management clients for unused screens. If BFF spec delivery is deferred, explicitly agree handwritten minimal DTOs instead of inventing a fake production spec.
7. Replace bootstrap smoke test with observable router/session workflows and HTTP-boundary tests. Add only necessary shared MSW setup/fixtures under `src/test`. Update Backoffice README with actual setup, browser contract, proxy, backend prerequisites and commands. Update canonical contract documentation only where the approved BFF handoff requires it; do not rewrite ADR decisions.

Likely frontend files: `src/main.tsx`, `App.tsx`/`App.test.tsx`, `index.css`; new `src/app/*`, small `src/routes/*`, `src/features/auth/*`, landing page, `src/shared/api/*`, `src/shared/ui/*`, `src/test` support; `vite.config.ts`, `vitest.config.ts`, `orval.config.ts`, `components.json`, README. Create folders only as code needs them. Lockfile changes only if required by the approved implementation. BFF code/spec/CI/infra changes belong to the separate dependency unless explicitly added to #14.

## Minimal meaningful verification

- Router + MSW integration: initial session loading does not flash protected content; 401 shows login entry; authenticated reload/deep link opens shell; network failure offers retry; unknown route behaves deliberately; safe return navigation and external redirect rejection.
- Session transitions: login initiates BFF navigation; logout sends CSRF and clears protected UI/cache only with defined outcome; failed logout is visible; expiry returns to login without loops; late in-flight responses cannot repopulate a prior session's cache.
- HTTP boundary: correct credentials/CSRF behavior; 403 retains session; known/unknown Problem Details, validation pointer data, malformed/non-JSON/network errors and cancellation handled safely. No redundant assertions for generated methods or styling markup.
- Shell interaction: keyboard navigation, navigation toggle, pending logout, heading/landmark/error accessibility and representative narrow/wide layout manual checks.
- From `web/backoffice`: `corepack pnpm install --frozen-lockfile`, `corepack pnpm format:check`, `corepack pnpm lint`, `corepack pnpm test`, `corepack pnpm build`. Verify API generation when integrated and inspect deterministic output/diff. Use Node 24/pnpm 12.9.1 as existing CI specifies.
- Integrated environment, once BFF exists: real Identity login+PKCE callback → session → landing → logout → protected route rejected; cookies/CSRF rejection, expired session and agreed Identity SSO logout behavior. Do not count MSW coverage as successful real login. Run Playwright only in the separate E2E repository when available; otherwise report manual integrated checks and missing automation explicitly.

No verification commands were executed during this planning task; evidence is source/configuration inspection, issue retrieval and Git state/history.

## Compatibility, rollout and risks

- No database migration or existing business API/security change is needed for the frontend scope. BFF sessions must use Redis rather than Identity tables.
- Agree contract first; deliver/deploy BFF and matching Identity client configuration before enabling real FE login. Proxy/ingress and callback host/scheme must agree. Keep mock handlers test-only or explicitly gated development mode; never silently fall back to mock auth in production.
- Access tokens expire after five minutes; refresh registration is off by default. BFF must agree reauthentication versus refresh, session TTL/invalidation and logout of Identity SSO. FE cannot decide token rotation itself.
- Without BFF, frontend tests/build may pass but issue login/logout acceptance remains incomplete. Ecommerce connectivity cannot be demonstrated with nonexistent business endpoints. The session boundary is the initial real backend interaction; downstream token relay must be verified by BFF integration tests without adding artificial production endpoints.

## Confirmed scope decision

The user selected a separate BFF CR. Issue #14 stays frontend-only and integrates against the agreed browser contract. This decision does not authorize implementation or creation of the dependency issue.

## Decisions requiring approval

1. With BFF owner: exact browser contract above, session/expiry/logout semantics and same-origin local/deployed routing. Settle before coding auth integration; implementation of the accepted BFF stack itself does not require a replacement architecture.
2. Confirm minimal session-only landing satisfies #14's initial API integration while Ecommerce has no business endpoints. Any additional real API interaction needs a separately agreed use case, not a speculative feature.

Optional later work: additional business navigation/primitives, comprehensive translations, theme switching and visual regression suites. None is required for this foundation.
