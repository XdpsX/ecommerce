# Issue #24 — Category Management UI implementation plan

Status: proposed for review; no implementation authorized by this plan.
Issue: https://github.com/XdpsX/ecommerce/issues/24
Inspected: 2026-11-14. Target: `phase/2-catalog-management`.

## Recommended scope

Deliver Category List and Structure screens, a shared create/edit drawer, explicit move/reorder/delete interactions, and a small reusable Media selection/direct-upload feature. Reuse the BFF session, CSRF transport, generated clients, TanStack Query, i18n and existing Base UI theme. Add only the backend/BFF contract support needed by these screens. No Media Library, attachment tracking, Product implementation, OAuth refresh redesign or general backend refactor.

The backend from #23 is available. This is not exclusively a frontend change: browser Media routing/scopes are missing, configurable maximum depth is not exposed, and arbitrary list sorting has no API support. The specific scope resolutions below require review before implementation.

## Branch and investigation evidence

- Clean working branch: `feat/24-category-management-backoffice-draft`, HEAD `4aea4982796763ec1a0acb55709aeca02ad3d849`, merging PR #25.
- Local and remote-tracking phase branch are at that same commit. `git rev-list --left-right --count origin/phase/2-catalog-management...HEAD`: `0 0`.
- Live read-only `git ls-remote origin refs/heads/phase/2-catalog-management` independently returned the same SHA. No fetch, sync, branch creation, checkout, commit or push performed.
- Issue is open with no comments and no assigned milestone in the fetched response; P2 follows the issue and milestone phase document. Do not silently change GitHub tracking.
- No nested AGENTS.md found under the affected frontend or BFF directories. Read root instructions, engineering/workflow/testing/code-organization guides and Backoffice, BFF, Ecommerce, Media and relevant Identity README sections.

| Source | Current behavior and implication |
| --- | --- |
| `contracts/openapi/ecommerce.yaml`; `services/ecommerce/.../category/adapter/web/CategoryController.java` | CRUD, flat complete structure, slug suggestion, move, reorder. Update uses entity version; delete uses entity version plus structure revision; move uses both; reorder uses structure revision. Create has no client revision. |
| `services/ecommerce/.../category/adapter/persistence/JpaCategoryPersistence.java:40` | Server list has filters/page/size but fixed `createdAt DESC, id ASC`; no sort parameter. Never sort only the current page as though it were the full result. |
| `services/ecommerce/.../category/application/service/CategoryService.java:174`; `:474` | Move appends to destination; no position field. Snapshot contains revision/categories, not configured max depth. |
| `web/backoffice/src/app/router/router.tsx`; `app/layout/components/PrimaryNavigation.tsx` | Only Dashboard/Login routes/navigation. No Category feature. |
| `web/backoffice/orval.config.ts`; `shared/api/client.ts` | Orval consumes only browser BFF contract. Mutator rejects cross-origin destinations, handles CSRF, cancellation and session generations. Do not use it for provider upload. |
| `services/backoffice-bff/.../config/{GatewayConfiguration,BffSecurityConfiguration}.java`; `application.yaml` | Only `/api/ecommerce/**` is relayed/authenticated; requested scopes are `openid,profile,ecommerce.api`. Media is not reachable through BFF. |
| `services/backoffice-bff/.../session/adapter/web/SessionController.java`; frontend `features/auth/session.ts` | Session exposes only subject; no permissions or actor-name lookup. Never infer access from roles or inspect JWT in browser. |
| `contracts/openapi/media.yaml`; `services/media/README.md` | Intent create/complete/status, metadata and private download-link exist; no asset-list/search API or FE upload implementation. Direct upload disabled by default, release checks outstanding. |
| Identity `authorization/adapter/oauth2/token/AccessTokenCustomizer.java:40`; Identity README | Both API scopes produce the appropriate audiences and audience-filtered permissions. Existing explicit `add-media-scope` CLI updates a registered client; existing sessions/tokens must be renewed. No new grant model required. |
| `docs/03-architecture/{ecommerce-category,media-service,media-direct-upload-security-operations}.md` | Independent image references. Missing assets get placeholders; READY does not imply attachment. Direct metadata residuals accepted, APNG excluded, replay/fanout controls remain required. |
| `.github/workflows/backoffice-ci.yml` | CI regenerates browser client and checks drift; currently does not trigger on Ecommerce/Media contract paths. Extend filters when these become generator inputs. |

Paths containing `...` above abbreviate the existing Java namespace, not proposed new directories.

## Decisions proposed for approval

1. **Sorting:** add a bounded server `sortBy=createdAt|updatedAt|name` and `sortDirection=asc|desc`, defaulting to existing `createdAt DESC`; always add `id ASC` tie-breaker. Validate the allowlist, never accept arbitrary property/SQL expressions. Apply before pagination. Update contract and relevant persistence/API tests. This is a small #23 API extension within #24, not an approved change yet. Alternative: fixed-order list without sort control, explicitly reducing the issue acceptance criterion.
2. **Move position:** select parent/root and append, then use a separately confirmed sibling reorder with full IDs and the latest snapshot revision. Explain append in UI; do not present two HTTP calls as atomic move-to-position. Keyboard Move up/down controls are sufficient; no drag/drop dependency. Alternative: expand backend to atomic positioned move, requiring a separate agreed contract. Recommendation is append + explicit reorder.
3. **Depth:** add `maxDepth` to the existing structure snapshot, populated from validated `CategoryLimits`. New UI consumes this value rather than hardcoding 8. Exclude self/descendants in move and use subtree height when filtering candidate parents. Backend remains authoritative. No configuration management endpoint or new DB migration needed.
4. **Image selection:** small Media feature supporting file upload and explicit existing asset UUID lookup/preview. No browsable library because no list API exists and Media Library UI was excluded earlier. Choose only verified READY results. Direct upload remains the FE path; no automatic or new FE multipart fallback. When direct issuance is disabled/unavailable, show that state and retain existing-image selection/KEEP/REMOVE and Category editing.
5. **BFF minimum scope:** add protected `/api/media/admin/media/**` forwarding to Media `/api/admin/media/**`, not a generic whole-service route or webhook proxy; preserve query/method and existing sanitization/CSRF/expiry/no-store rules. Add `BFF_MEDIA_URI` (local Media port 9001) and request `media.api`. Use existing operator CLI for client registration; no Identity source/DDL changes. Support media GET/POST required here; do not expose DELETE merely to clean abandoned form uploads.
6. **Permissions:** initial UI uses session authentication and authoritative per-operation 403 responses, with a dedicated read-forbidden screen. Do not add identity.admin scope or pretend subject-only session supplies fine-grained grants. Write/delete denial must preserve drafts and offer read-only continuation. If proactive permission-specific hiding/disabling is required, approve an additive server-owned capabilities contract separately; no raw tokens or client role guesses. This is a deliberate UX limit of the minimal proposal.
7. **Actor display:** render stable UUID plus localized unavailable-user fallback and localized timestamps. No new administrator-directory dependency, privileged Identity API, actor-name cache or invented display names. Enrich later only when a suitable approved lookup exists.

Items 1–6 settle material acceptance/integration gaps; item 7 follows the issue's explicit fallback. Product-reference delete errors can be rendered generically now, but real Product assignment/FK behavior is a P3 prerequisite, not something #24 can claim to test.

## Contracts and ownership

- Ecommerce remains sole Category owner. Browser routes `/categories` and `/categories/structure` call `/api/ecommerce/admin/categories...` through BFF. List URL state includes validated query/page/size/status/visibility and one mutually exclusive hierarchy filter: all, roots only, direct parent, or subtree root; sort only if approved. Changes reset page to zero. Structure always comes from full snapshot, never paginated list.
- Media owns intents/assets. Calls to create/complete/status/metadata/download-link go through session-authenticated BFF with CSRF on POSTs. Only image bytes go from browser to the returned, validated Cloudinary image-upload destination.
- Extend browser OpenAPI to describe cookie/CSRF paths and upstream body/response schemas using local schema references, keeping OAuth upstream security declarations out of browser operations. Preserve existing BFF operation IDs/session behavior. Avoid copying divergent Category/Media schema definitions.
- Update Orval local-reference allowlist and deterministic generation for these inputs. Confirm generated URLs include `/api/ecommerce/admin` and `/api/media/admin`, not upstream `/api/admin`. Do not hand-edit generated files or expose backend hosts to browser business API calls.
- No new database model/migration. Preserve existing JPA optimistic locking, structure CAS, Media lifecycle, JWT claims and Identity grants. Small optional sorting/depth additions require no change to locking semantics.

## Target frontend organization

```text
src/
  app/router/router.tsx                 # route registration/search adapters
  app/layout/components/PrimaryNavigation.tsx
  features/categories/
    pages/CategoryListPage.tsx
    pages/CategoryStructurePage.tsx
    components/CategoryFormDrawer.tsx
    components/CategoryForm.tsx
    components/CategoryParentPicker.tsx
    components/CategoryTree.tsx
    components/CategoryMoveDialog.tsx
    components/CategoryDeleteDialog.tsx
    api/categories.ts                  # query keys/options and invalidation
    model/{categoryForm,categoryTree,categorySearch}.ts
    locales/{en,vi}.json
  features/media/
    components/MediaAssetField.tsx       # upload / UUID lookup / preview
    api/media.ts                        # generated API coordination
    api/providerUpload.ts               # isolated credential-free transport
    hooks/useDirectUpload.ts            # bounded intent/upload/complete/status
    model/directUpload.ts
    locales/{en,vi}.json
  shared/api/{client.ts,generated/...}   # existing transport; generated output
  shared/components/ui/...              # only necessary business-neutral primitives
```

Colocate behavioral tests. Keep feature state outside shared UI; public Media entry exports only the field/value contract Category needs. No backend-style layers in React, giant usePage hook, universal form engine, generic upload framework, duplicate global stores or broad shell refactor. Reuse installed React Hook Form/Zod and existing theme; add only missing accessible primitives needed by these flows.

## Ordered implementation phases for Luna

Each phase should be independently reviewable; commit only with separate Git authorization. Do not start implementation until the proposed scope resolutions are approved.

### 1. Minimal Category contract support

- Files: Ecommerce OpenAPI; CategoryController, CategoryService query/snapshot models, CategoryPersistence/filter and JpaCategoryPersistence; existing Category IT/contract tests.
- Add approved sorting and `maxDepth` snapshot field; preserve defaults, filters, stable tie-break and transaction/precondition behavior. No positioned move or migrations.
- Verify sorting across page boundaries, rejected unknown sort fields, unchanged default order, snapshot depth matching non-default configured limit; run Ecommerce verify with its packaged Identity prerequisite.

### 2. BFF Media integration and browser contract

- Files: BFF `config/GatewayConfiguration`, `BffSecurityConfiguration`, `application.yaml`, existing BffFlowIT; browser OpenAPI, Orval configuration/generated output; FE CI path filters; `.env.example` and service READMEs for the new non-secret URI setting.
- Add narrow Media route and requested scope. Reuse TokenRelay, expiry, header sanitization and CSRF. Keep unknown routes and callback proxy denied. No refresh or token disclosure.
- Expand existing real OAuth/Redis fixture with JWT-validating Media downstream; verify Media audience/permission relay, missing scope, anonymous/expired session, CSRF/Origin denial, spoofed Authorization/user headers, cookies/Set-Cookie removal and upstream failures. Real Cloudinary is not needed for this fixture.
- Document explicit add-media-scope CLI and re-login; startup config does not automatically change persisted clients. Re-run existing Ecommerce relay/session regressions.
- Generate twice without drift; verify browser paths/security schemas and CI filters for all generator inputs. Run BFF verify after packaged Identity build, frontend checks after generation.

### 3. Category API/model foundation and List

- Add feature query keys including all filter/sort/page inputs; reuse shared mutator and session isolation. Register authenticated routes and navigation, bilingual namespaces and URL validation.
- Build responsive server-paginated List with search, compatible hierarchy filters, status/visibility, supported sort and reset pagination; breadcrumb/parent, status/visibility/effective visibility and actions.
- Distinguish initial loading, refetch, empty/no-results, forbidden, error/retry and out-of-range page after deletion. Never label stale data as a complete tree. Cancel obsolete searches; no automatic unsafe replay.
- Tests: URL round-trip/back/forward, real query parameters, page/filter reset, delayed response races, empty/403/network states and session cache clearing. Add only needed shared form/dialog primitives with label/focus behavior.

### 4. Create/edit forms and concurrency recovery

- Shared desktop right drawer/mobile full-screen presentation, compact sections, focus restoration/trapping and unsaved navigation/close confirmation. Initialize form once per chosen entity/version; background refetch must not overwrite dirty input. Browser unload warning is best effort, not the sole guard.
- Create: server slug suggestion with cancellation/debounce; only latest response applies while slug remains automatically managed. Manual editing stops replacement; offer explicit regenerate. Required name, validated limits, optional description, root/parent breadcrumb/preview, INACTIVE/PRIVATE defaults. No order/audit inputs or client create revision.
- Edit: stable saved slug on name change, no parent field mutation; separate Move action. Map image state explicitly to KEEP/SET/REMOVE and omit imageAssetId for KEEP/REMOVE. Show immutable audit fields with UUID fallback and locale-aware timestamps.
- Map known Problem codes and field pointers to localized errors; unknown errors get safe localized fallback. Duplicate slug can be mapped to slug by machine code even without a field pointer. Do not depend on English detail text.
- Entity conflict preserves draft, fetches latest separately, offers compare/reload and explicit resubmission using the reviewed version. Never silently update expectedVersion and retry. Pending disables duplicate submit; uncertain network mutation outcome requires reconciliation/user review, not automatic POST replay.
- Tests: root/child defaults, manual slug vs delayed suggestions, edit slug stability, dirty guards, versions, field errors and pending clicks. Media initially integrates through the narrow value contract, implemented in phase 6.

### 5. Structure, Move, Reorder and Delete

- Derive hierarchy/index/breadcrumbs from full snapshot ordered by displayOrder; preserve private/inactive nodes and explain inherited effective visibility. Expanded state stays local. Prefer nested semantic lists and disclosure buttons; use ARIA tree roles only if implementing their complete keyboard pattern.
- Searchable parent picker excludes self/descendants and enforces server maxDepth using moved subtree height; authoritative errors still handled. Move appends; reorder sends every sibling UUID exactly once using snapshot revision, including hidden/inactive siblings. No filtered subset reorder.
- Accessible up/down buttons with pending/edge disabling and live announcements; no speculative DnD. Snapshot conflict refreshes structure, retains desired operation for review, requires explicit repeat. Never auto-retry stale structural writes.
- Delete confirmation uses reviewed entity version and full snapshot revision; child/resource-in-use denial never cascades or deletes Media. Refetch before proposing a retry; concurrent changes remain visible.
- Invalidate Category list/detail/structure after successful mutations, including ancestor-derived visibility and sibling audit/version changes. Do not synthesize partial optimistic tree mutations. On uncertain outcomes refresh state before new commands.
- Tests: complete tree despite list pagination, visibility, expansion keyboard behavior, cycle/subtree depth, full sibling request, stale revision/entity, changed/deleted target, delete restriction and no image delete call. Product-specific restriction test is an HTTP fixture only until P3 exists.

### 6. Reusable Media field/direct-upload workflow

- Single Category image; existing asset UUID lookup confirms metadata READY, missing/unavailable previews use placeholder. Replacement/removal changes only draft reference. Do not delete a READY asset on cancel, failed Category save, replace, remove or Category delete.
- File select validates JPEG/PNG/size as UX only; server/provider remain authoritative. Create one intent through batch API, upload exact returned fields/file, complete then poll status with bounded backoff and explicit stop/expiry rules. Handle PENDING/RETRYABLE and every terminal status. Only READY with mediaId can become selected Category image.
- Keep provider request separate from shared API mutator: HTTPS, expected Cloudinary upload host/path and image resource type, no app cookies, OAuth header, CSRF header or secrets. Validate instructions before sending file; never trust provider response as READY. Do not log signed fields/private URLs.
- Unknown provider-upload outcome proceeds to intent completion/status; do not silently create another intent or fallback to multipart. A new upload is an explicit user action. Known intent recovery can continue while mounted; V1 does not persist signing fields in localStorage or add cross-tab/resumable-upload machinery.
- Cancellation/logout/session change stops local provider work and late UI updates; abort cannot promise remote deletion. Guard results by session/form selection generation so an old upload cannot attach to a new draft/user. Revoke object URLs and timers on disposal.
- Preview uses Media download-link API and transient signed URLs, with expiry/missing/error placeholder; no permanent URL stored in Category or logs. Honor image alt/i18n/referrer policy. Bound in-flight work; no eager link requests for an entire full tree.
- Tests with MSW/fake provider transport: required CSRF and no provider credentials, exact fields, invalid destination, partial/terminal responses, lost responses, bounded polling, session/unmount races, stale selection, KEEP/SET/REMOVE and no cancellation delete. Do not call Cloudinary in ordinary frontend CI.
- Direct upload stays disabled in production until existing deployed-account/APNG/privacy/recovery release checks pass; this UI change cannot waive those checks. Mock success is not release evidence.

### 7. Integration, accessibility and durable documentation

- Connect Media field to both forms. Test root/child creation, edit without image/slug/parent loss, ancestor visibility change, move/reorder/delete conflicts and repeat entry after session renewal.
- Verify keyboard-only open/edit/close, focus return, unsaved confirmation, error announcements, mobile drawer/dialog/scroll layouts, translated labels/errors/timestamps and long Vietnamese names.
- Update Backoffice/BFF READMEs, Category architecture/API docs and Media browser integration notes; link existing independent-reference/direct-upload security policy rather than redefining lifecycle. Add reusable QA only if approved; local walkthrough goes in ignored `.agent-work/`.
- Frontend commands from `web/backoffice`: `corepack pnpm generate:api` twice, `format:check`, `lint`, `test`, `build`. Confirm no generated drift. Do not modify unrelated dependency versions.
- Run affected backend verify suites with Java 25, Docker and UTC as documented. Ecommerce/BFF need packaged Identity. No full Media verify required absent Media source changes, but run real deployed Media/browser checks separately when authorized/configured.
- Manual integrated checks: real BFF login + renewed scopes, Category CRUD and conflicts, direct upload/preview with deployed Media, 401/403, missing asset placeholder, desktop/mobile. Use isolated approved provider environment; no further configuration experiments or account changes. Report checks not runnable; formal E2E remains separate repository.

## Rollout and risks

1. Deploy backward-compatible Category contract additions, then BFF route/scope configuration and browser contract/client/UI. Update registered BFF client with existing secure operator command and reauthenticate. Category-only use must not be disabled by unavailable Media.
2. Keep direct issuance release gate independent: environment/config checks, validated deployed provider evidence, browser CORS, private delivery/expiry, APNG rejection and recovery remain prerequisites. User can still keep/remove a Category image or choose an existing READY UUID without newly issuing upload authorization.
3. No cross-service transaction: uploaded READY image can outlive an abandoned form or failed Category save. Retain it; no frontend cleanup pretending it is unreferenced. Media deletion later produces placeholder, not Category failure/deletion.
4. Five-minute BFF token/session expiry can interrupt forms. Preserve in-memory drafts across recoverable checks where safe; do not persist sensitive drafts across users or promise survival across top-level OAuth navigation. Explain reauthentication/data-loss risk and test session boundaries.
5. Full snapshot is necessary but unpaginated. Single-shop V1 uses normal tree rendering; do not preemptively add virtualization/background workers. Avoid per-node Media calls. If actual dataset evidence requires limits, report separately.
6. UI cannot prevent concurrent edits; backend remains authoritative. Do not combine observations from paged list with unrelated revision to claim a consistent tree, or automatically retry writes after CSRF/session/network conflicts.

## Review handoff / open approvals

- Approve the minimal Ecommerce sort/maxDepth additions and BFF Media scope/route/browser-contract work as part of #24.
- Approve append Move + separately confirmed keyboard reorder rather than atomic move-to-position.
- Approve UUID lookup + direct upload as the asset picker without Media Library; direct-disabled handling must remain explicit.
- Confirm subject-only session with authoritative 403 handling is sufficient for V1, or request a separate minimal capabilities design for proactive action gating.
- Tests were not run for this read-only planning task. Evidence is source/contract inspection, issue/comments retrieval and live target-SHA read. Only this ignored plan artifact was created; no application, durable documentation, contract or Git mutations.
