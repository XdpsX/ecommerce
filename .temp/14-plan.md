# Issue #14 — Backoffice foundation plan

Status: Refined proposal; not approved for implementation. Separating BFF into its own CR is approved. Endpoint contracts and lifecycle details below remain proposed until explicitly approved in the BFF handoff.
Issue: https://github.com/XdpsX/ecommerce/issues/14

## Recommended scope

Implement the smallest usable Backoffice foundation in `web/backoffice`: accessible shared shell, navigation, login entry/logout, protected landing route, typed session/API transport and consistent error states. Keep the accepted BFF architecture. Deliver the missing BFF as a separate dependency CR, agreeing its browser contract before contract-dependent frontend implementation (G2) and integrating it before claiming the complete login acceptance criterion (G3). Independent foundation work (G1) can proceed after implementation approval.

Use `phase/1-core-services-foundation` as integration/PR base; the issue's `phase/1` is outdated. Initial source inspection used integration HEAD `52e525275498735d72da411ca12fcfa177c7286e` (PR #18 integrated). This refinement checked a clean working tree on `feat/14-backoffice-foundation-draft` at `c601f27e8a406966e9f5aa948f52c7395d03ff38`. This task explicitly updates `.temp/14-plan.md` only; retain that requested path for review, and do not introduce further temporary tracked docs. No branch creation/switch or application changes are authorized by this plan.

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

Approved: ADR-0001 architecture and the user's separate-BFF scope decision. No browser endpoint contract is approved yet. The proposals below are a handoff draft, not evidence that endpoints exist. BFF owns the server contract; Backoffice owns its consumer and tests. Before auth implementation, record explicit approval plus the checked-in spec path/revision, endpoint table, lifecycle/error decisions and local/deployed origin/callback settings. MSW fixtures must follow that approved revision; agreement on architecture alone does not approve endpoint details.

| Boundary | Owner / proposed behavior |
| --- | --- |
| Browser → BFF | Same-origin relative URLs through production ingress; Vite proxies matching paths locally. Cookie credentials, no browser Authorization header or client secret. |
| Session read | Proposed `GET /bff/session`: 200 `application/json` with `{ "subject": "<administrator UUID>" }`, no tokens; 401 Problem Details with `UNAUTHENTICATED` when missing/expired. No 200 anonymous variant. `Cache-Control: no-store`. No username/permissions/expiry timestamp without an agreed source and semantics. |
| Login | Proposed top-level navigation to `/oauth2/authorization/identity`; BFF owns code+PKCE exchange/callback. Return only to validated local app paths, excluding protocol-relative/external URLs. Identity hosts credential form; no frontend password grant/form. |
| CSRF | Proposed `GET /bff/csrf`: 200 JSON `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`, no-store; available before login where framework requires it. Keep in memory, send only to the same-origin BFF on unsafe requests, reacquire after session rotation. BFF validates unsafe requests including logout. Confirm actual header name, token lifecycle and missing/invalid-token error before implementation. |
| Logout | Proposed CSRF-protected `POST /bff/logout`: 204, no body, after invalidating Redis session, cookie and server-side authorized-client state. An already absent session has the same success result subject to agreed CSRF policy. Do not assume this revokes all Identity grants or ends Identity SSO. Global/Identity logout and any top-level redirect sequence require an explicit handoff decision; never follow arbitrary response redirect URLs. |
| Expiry | Session endpoint is authoritative; a session 401 moves the UI to unauthenticated. Session TTL/idle versus absolute timeout and refresh versus reauthentication belong to BFF. A downstream token 401 triggers one deduplicated session recheck, not automatic login or mutation replay. A valid BFF session with a failing downstream token displays a recoverable request error. No FE JWT parsing, expiry timer or refresh loop. |
| Backend API routing | BFF owns explicit route allowlists and server-side token relay. Preserve downstream status, Problem Details and relevant headers. Do not add a production Ecommerce probe to demonstrate connectivity. |
| Identity → BFF | Identity owns credentials, entitlements, OIDC grants and signed tokens. BFF owns browser session and OAuth client state. Browser receives no raw tokens. |

Session and API XHR endpoints must return statuses/JSON rather than redirecting fetch requests to an HTML login page. UI navigation can redirect interactively. BFF cookies require Secure/HttpOnly/scoped SameSite in deployment; document explicit localhost development settings separately. Do not solve local integration by broadly enabling CORS on resource servers.

HTTP semantics in the proposed handoff: session 401 means unauthenticated; permission 403 means forbidden and retains session. CSRF rejection must have an agreed distinct stable code (name pending), rather than treating every 403 as expiry. BFF-owned REST failures use the shared Problem Details schema, actual HTTP status and no-store; tolerate unknown codes/additional fields and render safe text. OAuth/OIDC navigation errors retain protocol semantics. Preserve downstream REST status/body and applicable headers; do not reinterpret a downstream Bearer challenge as a browser authentication mechanism. BFF session authentication uses cookies, so any challenge headers must be specified for that boundary rather than copying resource-server Bearer semantics. Network/5xx/HTML or malformed JSON errors mean unavailable/protocol failure, never successful authentication/logout. No unsafe request is automatically replayed after login or CSRF renewal.

## Implementation gates

| Gate | Permitted work after implementation approval | Required evidence / limit |
| --- | --- | --- |
| G1 — independent frontend foundation | Shell/tokens/primitives, provider and route composition, static landing/login entry presentation, safe return-path validation, common error parsing and tests with synthetic fixtures. | No production session DTO/endpoint assumptions, fake auth, new backend endpoint or generated BFF client. Test fixtures do not establish a contract. |
| G2 — approved BFF contract | Generate client; implement session guard, CSRF/login/logout wiring, transitions/cache isolation, Vite proxy and contract-aligned MSW tests. | Approved handoff and checked-in OpenAPI revision including error/lifecycle/origin decisions. BFF runtime may still be pending; report FE verification only. |
| G3 — integrated acceptance | Verify actual login/callback, session cookie, CSRF enforcement, expiry/logout and protected navigation. | Running BFF + Identity + Redis, registered client/callbacks and agreed local routing. Only this gate completes real authentication acceptance. |

Gate approval is not inferred from time passing or mocks passing. Missing G2 blocks contract-dependent work, not G1. Missing G3 blocks final authentication acceptance, not deterministic FE verification. No BFF implementation enters #14.

## API generation strategy

Use existing Orval with an approved checked-in BFF OpenAPI contract supplied by the separate BFF CR as the sole production generation input (proposed location `contracts/openapi/backoffice-bff.yaml`; exact path/revision is part of handoff). It references the shared Problem schema and describes consumed session/CSRF/logout responses. Login redirects remain explicit navigation behavior documented by the handoff, not a fake JSON API.

Replace the current live localhost default with that repository file; no running backend or network fetch is required for generation. Emit only consumed client/types under `src/shared/api/generated` using the shared Axios mutator for credentials/CSRF/error normalization; preserve the existing React Query generator choice without adding duplicate handwritten wrappers/hooks for every method. Generated files are committed and never hand-edited. Keep feature session orchestration outside generated code. Test error normalization once at the shared boundary.

No automatic handwritten DTO fallback is planned. If the approved spec is absent, stop G2 and continue G1; an exception needs separate approval. Do not fabricate a production spec/endpoint to unblock Orval, generate unused Identity management screens or alter backend contracts in this CR. Verify `corepack pnpm generate:api` twice produces identical outputs, build against them and inspect the generated diff. Ensure the selected input has no remote references needed for reproducible generation. Any generator configuration/normalization adjustment must be limited to the approved contract.

## Session transitions and cache isolation

Session states are `loading`, `authenticated(subject)`, `unauthenticated`, and `unavailable`; logout/revalidation are pending operations, not independent stores duplicating session data.

| Event | Result / observable behavior |
| --- | --- |
| Initial session check or return from login | Loading boundary hides protected content; 200 establishes principal, 401 shows login entry, transport/5xx/invalid payload shows unavailable with retry. |
| Login action | Top-level BFF navigation, preserving only a validated internal return path. Callback reload rechecks session; no optimistic authenticated state. |
| Session revalidation | Coalesce concurrent checks. Hide protected content while identity is uncertain; session 401 clears session-sensitive data, failure shows unavailable rather than claiming logout. |
| Logout starts | Disable repeated action, cancel sensitive requests and hide protected content while completion is uncertain. Suspend new protected requests. |
| Logout returns agreed 204 | Clear session-sensitive queries and CSRF state; set unauthenticated and replace protected navigation with public login entry. |
| Logout fails or response is lost | Do not claim success or restore stale protected UI. Show failure, clear sensitive cached content and perform one session reconciliation: 401 → unauthenticated; 200 → authenticated with visible logout failure/retry; unavailable → keep protected content hidden with retry. Do not blindly replay POST. A CSRF failure allows token reacquisition and a subsequent explicit user retry. |
| Downstream 401 / 403 | 401 triggers one session check without replaying the original operation; if still authenticated, show request failure. Permission 403 shows forbidden and retains identity. |

Use a minimal session generation counter at the auth composition boundary plus request cancellation/query removal; no generic session framework. Advance it before logout and when entering a new/unknown identity context. Cancel and remove identity-sensitive queries on confirmed logout, reconciliation and subject change; clear/reacquire CSRF accordingly. Responses/errors captured under an older generation cannot populate cache, update UI or invalidate a newer session. Subject-only keys are insufficient when the same user signs in again. Keep the session query authoritative, avoid persisted caches/token storage, and cover delayed-response races in tests. No multi-tab synchronization framework is added; each tab revalidates session on focus/protected entry and handles server 401.

## Ordered implementation

1. Keep BFF implementation in a separate CR, as approved by the user. Track the dependency and freeze the browser handoff before G2; do not open an issue without authorization. Begin independent G1 work after plan approval while that handoff is pending. Full login acceptance remains blocked until G3.
2. Compose TanStack Query and Router under `src/app`; use code-based routes to avoid unnecessary generation setup. Add public login, protected `/` landing, not-found and route error behavior. Session states: loading, authenticated, unauthenticated, unavailable. Network/5xx errors provide retry rather than pretending the user logged out. Validate any return path.
3. Add `src/shared/api` transport and typed Problem Details normalization. Use agreed same-origin browser contract and CSRF for unsafe requests. Distinguish 401, 403, structured validation, unknown code, non-JSON/malformed error, network failure and cancellation. Never parse `detail` for decisions, render untrusted HTML or log credentials. Do not blindly retry unsafe operations.
4. Add `src/features/auth` session/login/logout workflow integrated with route guards. Coordinate identity-sensitive Query cache cancellation/clearing and prevent late requests restoring previous-session data. Avoid infinite 401 retries/redirect loops. A downstream 401 requires session revalidation; a 403 is forbidden, not evidence the session expired. Server authorization stays authoritative.
5. Build minimal design foundation: consistent color/type/spacing tokens, focused shared button/loading/error primitives, responsive navigation, header and main content. Use installed Base UI/Tailwind/icon/font tools only where needed. Provide visible focus, keyboard controls, semantic landmarks, skip link and accessible collapsed navigation. Landing contains a truthful welcome; no fabricated analytics, empty business links or role-management UI. Choose neutral styling and existing English copy as provisional defaults; no new i18n/theme framework work.
6. Align `components.json` aliases only for primitives actually added, following `shared/ui` convention. At G2, update Vite proxy/test environment and integrate Orval exactly as specified by the generation strategy above. No handwritten DTO fallback or fabricated spec.
7. Replace bootstrap smoke test with observable router/session workflows and HTTP-boundary tests. Add only necessary shared MSW setup/fixtures under `src/test`. Update Backoffice README with actual setup, browser contract, proxy, backend prerequisites and commands. Update canonical contract documentation only where the approved BFF handoff requires it; do not rewrite ADR decisions.

Likely frontend files: `src/main.tsx`, `App.tsx`/`App.test.tsx`, `index.css`; new `src/app/*`, small `src/routes/*`, `src/features/auth/*`, landing page, `src/shared/api/*`, `src/shared/ui/*`, `src/test` support; `vite.config.ts`, `vitest.config.ts`, `orval.config.ts`, `components.json`, README. Create folders only as code needs them. Lockfile changes only if required by the approved implementation. BFF code/spec/CI/infra changes belong to the separate dependency unless explicitly added to #14.

## Minimal meaningful verification

- Router + MSW integration: initial session loading does not flash protected content; 401 shows login entry; authenticated reload/deep link opens shell; network failure offers retry; unknown route behaves deliberately; safe return navigation and external redirect rejection.
- Session transitions: login initiates BFF navigation; logout sends CSRF and clears protected UI/cache only with defined outcome; failed logout is visible; expiry returns to login without loops; late in-flight responses cannot repopulate a prior session's cache.
- HTTP boundary: correct credentials/CSRF behavior; 403 retains session; known/unknown Problem Details, validation pointer data, malformed/non-JSON/network errors and cancellation handled safely. No redundant assertions for generated methods or styling markup.
- Shell interaction: keyboard navigation, navigation toggle, pending logout, heading/landmark/error accessibility and representative narrow/wide layout manual checks.
- From `web/backoffice`: `corepack pnpm install --frozen-lockfile`, `corepack pnpm format:check`, `corepack pnpm lint`, `corepack pnpm test`, `corepack pnpm build`. Verify API generation when integrated and inspect deterministic output/diff. Use Node 24/pnpm 12.9.1 as existing CI specifies.
- Integrated environment, once BFF exists: real Identity login+PKCE callback → session → landing → logout → protected route rejected; cookies/CSRF rejection, expired session and agreed Identity SSO logout behavior. Do not count MSW coverage as successful real login. Run Playwright only in the separate E2E repository when available; otherwise report manual integrated checks and missing automation explicitly.

## Acceptance criteria and verification matrix

| Issue acceptance criterion | Implementation / FE evidence | Integrated evidence and completion gate |
| --- | --- | --- |
| Login/logout and protected navigation work | Auth route/session transitions, safe return path, pending/failure UX and cache isolation; Vitest + RTL + MSW (G2). | Actual Identity credential login → BFF PKCE callback → session → protected landing; logout → protected route rejected; agreed Identity SSO behavior (G3). FE tests alone do not complete this criterion. |
| Shared layout and API/error handling in place | Responsive accessible shell and landing (G1); generated typed session transport, CSRF and normalized REST failures (G2). | Real BFF session/error responses match approved contract, cookies/proxy routing work (G3). No invented Ecommerce endpoint or business metrics. |
| Core authenticated flows covered by frontend tests | Deterministic session loading/success/401/unavailable, logout success/failure/uncertain outcome, permission 403, downstream 401, redirect validation and old-session response races (G2); lint/format/build pass. | Report integrated checks separately; they supplement rather than replace FE tests. |

| Verification | Layer / dependency | Required assertion |
| --- | --- | --- |
| Return-path validation, Problem normalization | Vitest UT; no backend | Reject external/protocol-relative destinations; unknown codes/malformed/non-JSON/network errors use safe fallback and actual status. |
| Shell/routes/session | RTL + Router + MSW; approved contract at G2 | No protected-content flash; correct transitions, retry, 403 versus 401, logout uncertainty and cache races, including a previous-session error arriving after a new login. |
| UI interaction | RTL/user-event plus manual narrow/wide checks | Keyboard/focus, landmarks, collapsed navigation, disabled pending action and visible failures. No styling snapshots required. |
| Generated client | Approved repository spec + Orval + TypeScript | Repeated generation stable; no live source, missing mutator, unused API expansion or hand-edited output. |
| Repository checks | Node 24/pnpm 12.9.1; existing FE CI | Frozen install, format:check, lint, test, build; run generation separately when integrated and report result. No formatter write during planning/review. |
| Real authentication | BFF/Identity/Redis + actual browser (G3) | Login/callback/session/logout, HttpOnly/Secure/SameSite settings for environment, missing/invalid CSRF rejection, session expiry and agreed SSO logout. Confirm no OAuth tokens exposed to JS. |

Use integrated manual checks if the separate E2E repository/environment is unavailable and state that automation was skipped. Do not add a new local E2E suite merely to satisfy this CR. This plan refinement ran source/configuration/Git inspection only; no frontend tests, generation, build or real authentication checks have run.

## Compatibility, rollout and risks

- No database migration or existing business API/security change is needed for the frontend scope. BFF sessions must use Redis rather than Identity tables.
- Agree contract first; deliver/deploy BFF and matching Identity client configuration before enabling real FE login. Proxy/ingress and callback host/scheme must agree. Keep mock handlers test-only or explicitly gated development mode; never silently fall back to mock auth in production.
- Access tokens expire after five minutes; refresh registration is off by default. BFF must agree reauthentication versus refresh, session TTL/invalidation and logout of Identity SSO. FE cannot decide token rotation itself.
- Without BFF, frontend tests/build may pass but issue login/logout acceptance remains incomplete. Ecommerce connectivity cannot be demonstrated with nonexistent business endpoints. The session boundary is the initial real backend interaction; downstream token relay must be verified by BFF integration tests without adding artificial production endpoints.

## Confirmed scope decision

The user selected a separate BFF CR. Issue #14 stays frontend-only and integrates against the agreed browser contract. This decision does not authorize implementation or creation of the dependency issue.

## Decisions requiring approval

1. Approve the browser handoff with the BFF owner: checked-in OpenAPI path/revision and DTOs/statuses, CSRF policy/code/header, logout response/idempotency and local versus Identity SSO logout, session lifetime/refresh policy, same-origin routing and callback/return allowlists. These block G2, not independent G1 work. ADR-0001 remains unchanged.
2. Confirm minimal session-only landing satisfies #14's initial API integration while Ecommerce has no business endpoints. Any additional real API interaction needs a separately agreed use case, not a speculative feature.

Implementation itself still requires plan approval. The proposed generation strategy is explicit: approval of this refined plan selects checked-in-contract-first generation; it does not approve the still-proposed endpoint contract or a handwritten fallback. G3 environment availability is a delivery dependency, not authorization to implement BFF here.

Optional later work: additional business navigation/primitives, comprehensive translations, theme switching and visual regression suites. None is required for this foundation.
