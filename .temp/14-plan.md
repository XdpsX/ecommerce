# Issue #14 — Backoffice and BFF foundation plan

Status: Expanded implementation approved on 2026-11-07. BFF is included in #14. Decisions: no refresh; strict access-token expiry with zero additional skew; local BFF logout only; Vite 3000/BFF 8081; mandatory Redis client/session restart and logout verification. No commits, pushes or PRs authorized.
Issue: https://github.com/XdpsX/ecommerce/issues/14
Base: `phase/1-core-services-foundation` (not the outdated `phase/1`).

## Scope and current evidence

Deliver one coherent authentication foundation across `web/backoffice`, `services/backoffice-bff`, browser OpenAPI contract, scoped configuration/docs and CI. The earlier decision to create a separate BFF CR is superseded. Do not create another issue, change branches, commit, push or open a PR without separate authorization.

Preserve G1: temporary Dashboard at `/` with no fake metrics/business API, responsive shell, JSON shared/feature English-Vietnamese namespaces, language persistence/fallback/html.lang, config and separate providers composed by AppProvider, layout/components, development-only Query/Router Devtools, validated Problem Details and return-path helpers. `/` becomes protected in the completed implementation. Guards belong in `app/guards`, not the router directory. Keep the small footer inline and stable QueryClient/router lifetime.

Pre-implementation evidence (historical G1 baseline):

| Source | Observed behavior / implication |
| --- | --- |
| `web/backoffice/src/app/{config,providers,layout,router}`, `app/i18n.ts`, shared/feature locales | G1 organization and UI are implemented; session guards, CSRF, login/logout and typed browser integration remain absent. |
| `web/backoffice/src/shared/{api/problem.ts,lib/returnPath.ts}` | Existing validation and normalized-path safeguards must survive integration. |
| `services/backoffice-bff/.gitkeep` | BFF remains a placeholder, now to be implemented in #14. |
| `docs/03-architecture/adr/0001-backoffice-bff-as-api-gateway.md` | Accepted WebFlux Gateway confidential client, Redis Spring Session, server-side OAuth tokens, cookie/CSRF boundary; do not modify this ADR. |
| `services/identity/pom.xml`, Identity README/client/security source | Java 25 / Boot 4.1.2; code + PKCE S256 confidential client, five-minute access tokens, refresh grant off by default; UserInfo exposes only subject. Explicit callback/secret provisioning required. |
| `services/ecommerce/README.md` | No business API; JWT checks and authorization are independent of BFF. No production probe may be invented to prove relay. |
| `infra/compose/compose.yml`, `.env.example` | Password-protected Redis and PostgreSQL exist; reuse them. BFF gets its own Redis session namespace, no Identity database access. |
| `web/backoffice/orval.config.ts`, `contracts/openapi` | Generation currently targets an unestablished live URL/missing mutator; no BFF spec exists. |
| `.github/workflows/{backoffice,ecommerce,identity}-ci.yml` | Existing verification patterns; BFF needs its own workflow and browser-contract changes must trigger affected FE verification. |

Last G1 review: 25 FE tests, lint, TypeScript and production bundle passed using installed tools; frozen-install reproducibility and browser checks were not established. This is previous evidence, not verification of future BFF work. Current branch/worktree must be rechecked before implementation; preserve user commits and changes.

Out of scope: business screens, fabricated dashboard data, catalog/order/admin CRUD, Media workflows, Storefront/BFF, separate gateway, role-model redesign, distributed immediate revocation, unrelated service refactors, shared database, new infrastructure or speculative frameworks.

## Ownership and architecture

Browser → BFF: same-origin cookie-authenticated requests and CSRF. BFF → Identity: confidential OIDC Authorization Code + PKCE, token exchange and defined logout interoperability. BFF → resource servers: explicit Gateway routes with token relay; downstream validates issuer/signature/audience/permissions independently.

Identity owns administrators/credentials/grants/entitlements and signing keys. BFF owns Redis browser sessions and session-bound authorized-client state. Browser receives minimal subject/session information and a CSRF token, never OAuth tokens, secrets or serialized authorized clients. No username/roles/permissions are assumed from current UserInfo.

Selected and verified: Java 25, Spring Boot 4.1.2 and Spring Cloud 2025.1.4 (Gateway 5.0.4), with the compatibility verifier enabled. Do not disable the compatibility verifier or upgrade unrelated services to force compatibility. Use Gateway Server WebFlux, OAuth2 client and reactive Redis Spring Session; no MVC/JDBC/Flyway dependency in BFF.

Keep OAuth authorized-client state bound to the Redis-backed WebSession using a supported repository/manager integration. Do not assume the default in-memory authorized-client service persists just because WebSession uses Redis. Prove relay works after BFF restart with the same session, and logout/expiry removes access to the authorized client. Missing client state fails closed into reauthentication; no custom global principal-keyed token store or shared DB. If framework integration cannot satisfy this directly, report the concrete gap before introducing a custom token store.

## Frozen browser contract — approved v1.0.0

The approved endpoint decisions are frozen in checked-in `contracts/openapi/backoffice-bff.yaml` v1.0.0 referencing `common/problem.yaml`. This freeze preceded contract-dependent frontend integration; code, generation and tests use that revision. BFF is now the producer in the same issue, not an external CR dependency.

| Boundary | Approved behavior |
| --- | --- |
| `GET /bff/session` | 200 JSON `{ "subject": "<administrator UUID>" }`; 401 Problem `UNAUTHENTICATED` when missing/expired. No anonymous 200. No-store. Never create an authenticated result from frontend state alone. |
| `GET /bff/csrf` | 200 JSON `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`, no-store; materialize the framework token, including before login when needed for logout/anonymous CSRF handling. Keep token in memory; renew after login/session rotation. |
| Login | Top-level navigation to `/oauth2/authorization/identity`. BFF exchanges code with secret and PKCE S256 and validates OIDC/state/nonce. Callback `/login/oauth2/code/identity`. No password grant or credential form in FE. |
| Return destination | Default `/`; allow only validated app-local paths, excluding login/protocol endpoints to prevent loops. Validate normalized output on server as well as FE. Store return target in session; do not allow arbitrary redirect URLs. |
| `POST /bff/logout` | CSRF-protected; 204 after invalidating local session, cookie and client state; idempotent for absent session with a valid CSRF token. Missing/invalid CSRF yields 403 `CSRF_INVALID`. No automatic POST retry. Identity SSO may remain active; local-only logout is approved. |
| REST errors | Shared Problem Details and actual status; no-store for BFF-owned security/session responses. Session 401 means unauthenticated. Permission 403 retains session. CSRF code is distinct from permission denial. Unknown codes/malformed JSON/network errors use safe localized fallback. No HTML login redirect from XHR endpoints. |
| OAuth errors | Keep OAuth/OIDC protocol responses and navigation handling; expose safe login failure UI without raw provider diagnostics. REST advice must not rewrite protocol endpoints. |

API generation: existing Orval consumes only the approved repository spec, no live URL or remote refs. Emit consumed client/types under `shared/api/generated`, use one shared Axios mutator for credentials/CSRF/errors/cancellation, and avoid duplicate method wrappers. Generate twice and verify identical output plus build. No fabricated endpoint/spec or handwritten DTO fallback to bypass approval.

## Selected session, expiry and local logout behavior

Approved first slice: no refresh-token support. Reauthentication is required when the server-owned access token can no longer be used. Usable expiry is the earlier of the token response expiry and JWT exp, with zero additional skew: strictly before that instant the token is usable; at or after it the next session/API request invalidates authentication and returns 401. Redis idle timeout is 30 minutes and cannot extend this bound. Missing authorized-client state fails closed. Redis outage returns safe 503/unavailable. Controlled-clock integration tests verify the exact bound against the actual Redis-persisted authorized client.

Approved logout: local BFF logout only. Framework security/CSRF cleanup precedes invalidating the complete Redis session, then 204 is emitted. Old-cookie reuse is rejected. Identity SSO may remain active and subsequent login may be automatic. Logout does not promise immediate revocation of previously issued JWTs at downstream servers. Refresh or Identity end-session integration would require separate approval and is outside this implementation.

| Event | FE result / security behavior |
| --- | --- |
| Bootstrap/callback reload | Loading hides protected content; session 200 authenticates, 401 shows login, failed/invalid response means unavailable with retry. |
| RequireAuth | Authenticated → protected outlet; unauthenticated → login with safe local return path; loading/unavailable are separate states. |
| RequireGuest | Unauthenticated → login entry; authenticated → Dashboard; loading/unavailable do not redirect prematurely. |
| Downstream 401 | One coalesced session recheck. Session expired → login. Still valid → request error; never replay unsafe request or loop redirects. |
| Permission 403 | Show forbidden, keep session. CSRF rejection renews token and requires explicit action retry. |
| Logout pending | Hide sensitive content, suspend new protected requests and prevent repeated action. |
| Logout confirmed | Clear sensitive cache/CSRF, advance session generation and replace navigation with public login. |
| Logout failed/uncertain | Do not claim success or restore stale content. Clear sensitive data; reconcile session once: 401 → logged out; 200 → authenticated with visible logout failure/retry; unavailable → protected content remains hidden. |

Auth composition owns a small generation counter and Query cancellation/removal. Reject stale response/error callbacks from prior generations, including same-subject relogin. No persisted sensitive cache. Revalidate on focus/protected entry; override the existing global refetch-on-focus=false for session where needed. No multi-tab coordination framework or duplicate global session store.

## Gateway, routing and deployment

- Agree explicit route prefixes and methods; a proposal is `/api/ecommerce/**` → existing Ecommerce `/api/**`. Add only routes needed by the approved foundation; no generic arbitrary-URL proxy, exposed actuators or automatic discovery. Identity protocol URLs stay at Identity and explicit BFF OAuth endpoints.
- Strip incoming Authorization and spoofable identity/forwarded headers before server-side token relay; do not forward browser Cookie/CSRF headers to resource servers or relay downstream Set-Cookie into the browser. Preserve downstream status, Problem body and applicable protocol headers. Never forward the session cookie/client secret to provider APIs.
- Relay is proved with test-only HTTP fixtures/JWT-aware resource-server tests, not a production Ecommerce probe. No Media management feature is added.
- Proposed local BFF port 8081, Vite remains 3000. Proxy `/bff`, `/oauth2` and `/login/oauth2` to BFF, not SPA fallback. Register Identity callback using browser origin `http://localhost:3000/login/oauth2/code/identity`; use localhost consistently. Test the reconstructed redirect URI through proxy before accepting it.
- Production uses one HTTPS browser origin, Secure/HttpOnly host-only session cookie with appropriate SameSite=Lax and Path=/; different session cookie name from Identity. Explicit local profile permits HTTP cookie only for localhost. Cookie identity is not trusted as a downstream bearer credential.
- Trust forwarded headers only at the configured trusted ingress/proxy boundary; strip untrusted inbound values, configure exact public origin/callback allowlists and test spoofed Host/forwarded origin. No broad service CORS.
- Add BFF-prefixed config for port, Redis connection/namespace, Identity issuer/client secret and downstream URL; no real secrets in tracked config or VITE variables. Follow services' `local` profile import from `services/backoffice-bff` working directory. Existing Identity registration command remains operator-only; never seed credentials automatically.
- Deliver contract/server/client in sequence within this issue; register client/configure keys before integrated startup. No BFF PostgreSQL migration or production database modification. Do not modify applied Identity migrations.

## Implementation gates and ordered work

| Gate | Work / evidence |
| --- | --- |
| G1 — existing | Preserve approved shell/dashboard/i18n/organization/helpers. It does not complete the issue. |
| G2a — contract/design | Approve endpoint/session/logout choices and exact compatible dependencies; check in the real browser contract and implementation setup. This is a stage of #14, not a separate CR. |
| G2b — BFF implementation | Implement minimal WebFlux security/session/CSRF/login/logout and Gateway relay; verify real Redis lifecycle and Identity interoperability. |
| G2c — FE integration | Generate from approved spec, add session/auth feature, RequireAuth/RequireGuest under `app/guards`, protected Dashboard, login/logout UX, shared transport, cancellation/cache isolation and translated state UI. |
| G3 — acceptance | Actual browser + BFF + Identity + Redis checks and applicable CI pass. Only then close #14. |

1. Recheck branch HEAD/worktree; preserve G1/user changes and obtain expanded-plan/contract approval. Select and pin the compatible GA Cloud BOM without unrelated version churn.
2. Add BFF Maven wrapper/POM, non-blocking dependencies, minimal service/security/session/gateway configuration and test scaffolding. No empty layer boilerplate or common security library.
3. Implement actual spec/session/CSRF endpoints and confidential login with PKCE; prove Redis-backed authorized-client lifecycle and safe errors. Implement approved expiry/logout behavior and exact route/header allowlists.
4. Integrate FE generated client and both guards. Replace informational sign-in with BFF navigation, add logout pending/failure and safe return flow; preserve JSON namespaces/devtools-only behavior.
5. Update `.env.example`, service/Backoffice/infra README as needed, browser contract docs and new BFF CI. Update frontend CI path filters for BFF spec/common contract and generation verification. Scope Identity/Ecommerce changes only to reproduced interoperability gaps, with focused regression tests.
6. Run Maven/FE/integrated verification, fix introduced failures, review full diff; no commit/push/PR unless separately authorized.

Likely additions: `services/backoffice-bff/{pom.xml,mvnw,mvnw.cmd,.mvn,src/main,src/test,README.md}`, `contracts/openapi/backoffice-bff.yaml`, `.github/workflows/backoffice-bff-ci.yml`; FE auth/guards/transport/generation/proxy/test files, existing env/docs/CI. Use service capability + adapter/configuration organization proportionally; no mandatory domain or interface for every helper.

## Acceptance mapping and verification matrix

| Acceptance | FE tests | BFF / integrated proof |
| --- | --- | --- |
| Login/logout and protected navigation | RTL/Router/MSW success, loading/401/unavailable, both guards, safe return, pending/uncertain logout | Real packaged Identity code+PKCE callback → Redis session → protected dashboard → approved logout → rejected protected navigation. Missing/tampered state and incorrect callback rejected. |
| Shared UI/API/error foundation | i18n persistence/fallback, generated client, CSRF header, Problem normalization | No-store session/errors, actual 401/403/CSRF semantics, safe protocol errors, no sensitive diagnostics. |
| Core authenticated tests | Session transitions, identity-switch/same-user relogin races, stale error/response isolation, no unsafe replay | Redis restart/session reuse, token state availability after BFF restart, expiry cleanup/logout, fail-closed Redis outage. |
| Gateway security | Downstream failures do not corrupt current FE session | Real HTTP fixture verifies path/method allowlists, Authorization replacement, no Cookie/CSRF leakage, forwarded-header handling, status/Problem preservation. JWT resource-server fixture proves invalid audience/signature/permissions rejected. |
| Complete delivery | FE generation twice, lint/format/tests/build | BFF Maven verify + real browser cookie/CSRF/login/logout/expiry checks; docs/config/CI paths and environment prerequisites verified. |

Commands from repository root (PowerShell): build Identity producer with `& .\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package`, then BFF `& .\services\backoffice-bff\mvnw.cmd -f services/backoffice-bff/pom.xml -B --no-transfer-progress verify`. Before creating the new wrapper these are planned commands only. Use Java 25, Docker, Testcontainers Redis matching Compose 8.6.8 and PostgreSQL 18.6; process-local UTC for JDBC tests, preserving/restoring JAVA_TOOL_OPTIONS. No developer DB mutation or reusable test containers.

From `web/backoffice`: frozen install, `corepack pnpm generate:api` twice with deterministic diff, `format:check`, `lint`, `test`, `build`. Do not label direct installed-binary checks as a frozen-lockfile install. Report the known unchanged formatting baseline separately; no unrelated formatting sweep. BFF CI packages Identity before verify, uploads reports and triggers on BFF, Identity, relevant contract/workflow changes; FE CI must react to browser/common schema changes.

Frontend MSW tests remain deterministic without running services. Backend IT uses WebTestClient/security tests, real Redis for the session boundary and packaged Identity with PostgreSQL for OAuth interoperability. Use short controlled expiry fixtures rather than long sleeps. Framework-level mocks cannot substitute for these boundaries. Preserve existing downstream resource-server suites; run affected suites when integration changes touch them. No new production probe.

Real browser checks: local/deployed origin/callback correctness, cookies/CSRF, refresh or reauthentication policy, both guards, logout failure/reconciliation, approved SSO behavior, token absence from browser API/storage, and responsive/keyboard behavior in both languages. E2E automation belongs in the separate E2E repository when available; otherwise record manual evidence and the missing automation explicitly. No new E2E repository is created here.

## Approval record and implementation gates

1. Expanded plan approved by user. Browser OpenAPI v1.0.0 is frozen in contracts/openapi/backoffice-bff.yaml before contract-dependent FE work.
2. Approved no-refresh implementation: usable token must expire strictly after now, no skew extension; Redis 30-minute idle upper bound does not extend token validity.
3. Approved local BFF-only logout; Identity SSO may remain active and subsequent login may be automatic.
4. Approved local 8081/Vite 3000 arrangement, verified locally with real browser callback/cookie/CSRF checks and HTTP header-boundary integration tests. Boot 4.1.2 / Cloud 2025.1.4 pinned and Maven dependency resolution succeeds. Deployment secrets/hosts stay outside Git.

The Redis session-bound authorized-client choice and release compatibility are implementation verification gates. Report a demonstrated gap before adding a custom store or changing architecture. No ADR changes, speculative business API, new issue/PR or architecture changes are authorized. The user approved BFF implementation within #14.

## Official technical references

- Spring Cloud compatibility matrix: https://spring.io/projects/spring-cloud/ (Boot 4.1 supported by 2025.1.x starting at 2025.1.2; pin a verified GA patch during implementation).
- Reactive OAuth2 authorized-client repository/manager lifecycle: https://docs.spring.io/spring-security/reference/reactive/oauth2/client/core.html (do not confuse WebSession persistence with an in-memory authorized-client service).

## Implementation verification record

G2a → G2b → G2c → G3 local work is complete. The existing G1 source is preserved. Branch remains `feat/14-backoffice-foundation-draft`, HEAD `7dbc5c433a705b92717891ade193e61032aa20d6`; no commits/pushes/PRs/merges were made.

- Frontend: frozen install with pnpm 12.9.1, 34 tests, lint and production build pass; generated output is deterministic across 13 files.
- BFF Maven verify: 3 unit tests + 2 integration tests pass. Real Redis authorized-client persistence, BFF restart survival, exact expiry, no refresh, logout invalidation, relay boundaries and outage handling are covered.
- Identity Maven verify: 16 unit tests + 28 integration tests pass. A focused saved-authorization-request fix prevents an automatic favicon request from replacing the login continuation; regression test included.
- Actual local browser at Vite 3000 / BFF 8081 passes real Identity login/callback, protected dashboard, cookie/CSRF checks, Vietnamese mobile layout and local logout. No production Ecommerce endpoint was introduced.
- Hosted CI and production HTTPS ingress remain unexecuted. Default frontend format check reports CRLF in 10 unchanged baseline files; changed/new frontend files pass, and the full check with end-of-line auto passes. No unrelated formatting sweep was made.

Detailed reading guide, commands and limitations: `.agent-work/implementations/14.md`. Logs and browser evidence are ignored local artifacts under `.agent-work/`. No remaining architectural blocker requires changing the approved architecture.
