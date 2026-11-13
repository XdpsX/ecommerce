# Issue #23 — Category Management Backend

Updated: 2026-11-13. Status: approved design decisions incorporated; implementation checklist remains under review. This update authorizes documentation only, not application implementation.

Issue: https://github.com/XdpsX/ecommerce/issues/23

## Scope and approved decisions

Implement Category in the Ecommerce modular monolith with Spring Data JPA + Hibernate, Flyway migrations, an adjacency-list hierarchy and permission-protected admin APIs. Category entity concurrency uses JPA `@Version`; hierarchy/order concurrency uses an optimistic structure revision. No global pessimistic write lock, `SELECT FOR UPDATE` guard or pessimistic JPA lock is part of this design.

Create does not accept or require a client `expectedStructureRevision`: the backend handles structural contention. Move/reorder/delete use the client revision; reorder still requires the complete sibling ID list. Backend suggests Vietnamese-friendly slugs; an administrator may edit them. Renaming/moving never changes an existing slug implicitly.

Ecommerce stores nullable `imageAssetId` only. Media owns asset state and may delete an asset without checking Category references. Missing/unavailable images are an accepted condition; consumers display a placeholder. READY checks at assignment are optional, point-in-time checks. Attachment reservation, reference tracking, deletion protection, distributed compensation/reconciliation for references and Media Library UI are out of scope. This approved policy replaces the earlier Media prerequisite; images no longer block Issue #23 on a new attachment contract.

Also outside scope: Product/SKU implementation, Backoffice Category UI, storefront UI/delivery, redirects, soft delete/restore, event infrastructure, generic tree/attachment engines and cross-service database access. Existing Identity/Media persistence remains unchanged except the Category permission catalog addition in Identity.

## Current evidence and branch

- Working branch `feat/23-category-management-backend-draft`, HEAD `6d375c2efff63bd787d9309a41b1c7d511e4dc03` (`docs: add issue 23 plan`). Tracked working tree was clean before this update.
- `git rev-list --left-right --count HEAD...origin/phase/2-catalog-management`: ahead 1 / behind 0 against the locally available remote-tracking ref. No live remote refresh in this update. No synchronization performed; recheck before implementation.
- `services/ecommerce/pom.xml` currently uses Spring JDBC, not JPA. Only `V1__ecommerce_foundation.sql` exists and creates no business tables. JPA adoption below is planned, not implemented.
- `services/ecommerce/README.md`, `src/main/java/com/ecommerce/api/config/ResourceServerSecurityConfiguration.java`: UUID JWT subject, issuer/audience/RS256 validation, PERM_/SCOPE_ authorities, deny unknown routes; no Category API exists.
- Ecommerce `shared/error/{GlobalExceptionHandler,ApiProblems}.java` and `docs/04-contracts/http-errors.md`: existing Problem Details; detail remains a string, field errors use structured errors.
- `services/ecommerce/src/test/java/com/ecommerce/api/{ArchitectureBoundaryTest,PersistenceFoundationIT}.java`: existing package/migration/transaction tests; PostgreSQL 18.6 fixtures.
- Identity `V2__identity_admin_guard_and_catalog.sql`, `V6__media_entitlements.sql`: no Category permissions. `authorization/adapter/oauth2/token/AccessTokenCustomizer.java` already maps ecommerce.api to ecommerce-api and filters permissions by audience.
- BFF `config/GatewayConfiguration.java` already forwards `/api/ecommerce/**` to Ecommerce `/api/**`. BFF requests ecommerce.api, not media.api; an Ecommerce-only token cannot call Media metadata APIs.
- `contracts/openapi/media.yaml`, Media `asset/adapter/web/MediaAssetController.java` and `asset/application/service/MediaAssetService.java`: GET returns READY metadata with media.api/media-api/media:read authorization; DELETE has no Category lookup. The newly approved independent-reference policy matches this existing runtime boundary.
- Related durable docs: [architecture](../docs/03-architecture/README.md), [Media](../docs/03-architecture/media-service.md), [Media security/operations](../docs/03-architecture/media-direct-upload-security-operations.md), [code organization](../docs/06-development/code-organization.md), ADR-0001/0002. Legacy repositories are reference material, not an implementation contract; none were copied for this plan.

## Persistence and package organization

Use namespace `com.ecommerce.api.category`. Keep the domain independent of persistence frameworks: a small persistence entity and mapping at the adapter boundary satisfy the existing convention without putting JPA annotations into the domain or exposing managed entities over HTTP.

```text
com.ecommerce.api
  config/
    ResourceServerSecurityConfiguration                   existing, add route authorization
    CategoryConfiguration, CategoryProperties             validated settings/wiring
  shared/error/
    GlobalExceptionHandler, ApiProblems                    extend error mapping
  category/
    domain/
      model/Category, CategoryStatus, CategoryVisibility
      service/CategoryHierarchyPolicy, CategorySlugPolicy
      exception/                                         invariant errors when needed
    application/
      service/CategoryCommandService, CategoryQueryService
      model/                                             commands, views, snapshots, immutable settings
      port/CategoryRepository, CategoryQueries
      exception/                                         not-found/conflicts/in-use
    adapter/
      web/CategoryController, CategoryStructureController  transport DTOs grouped here
      persistence/
        CategoryEntity                                   JPA @Version here
        CategoryStructureStateEntity                     structure revision state
        SpringDataCategoryRepository
        JpaCategoryRepository, JpaCategoryQueries          implement application ports
```

Create only needed classes/subpackages. No extra catalog wrapper, shared Java library, interfaces per use case or bidirectional children collection/cascade is needed. Model parent as a scalar UUID in persistence unless a real query requires an association; the DB FK enforces existence. Native recursive SQL/projections and the revision CAS may live in the JPA adapter where JPQL is insufficient; do not build a parallel JDBC CRUD path.

- Replace Ecommerce's direct JDBC starter with Spring Data JPA starter using Boot-managed Hibernate versions. Flyway and PostgreSQL driver remain; JDBC utilities used by existing foundation tests need not be removed merely for style.
- Flyway alone owns DDL. Set `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`; do not use Hibernate create/update or automatic baseline. Map TIMESTAMPTZ, enums and UUIDs explicitly; test the Boot-resolved versions, not a newly pinned Hibernate dependency.
- Add Ecommerce V2 for `categories` and a one-row `category_structure_state` with BIGINT revision. Category fields: UUID ID, nullable parentId, name/slug/description, nullable imageAssetId, status, visibility, displayOrder, BIGINT version, UTC audit timestamps and actor UUIDs. No cross-service FKs. Use a nullable Java Long `@Version` for new entity detection; Hibernate initializes/increments it. Persisted version is nonnegative, normally starting at 0. Never copy request versions into the managed entity or increment Category versions manually.
- DB constraints: unique lowercase URL-safe slug, valid enums, nonblank bounded name, nonnegative order/version, parent FK with NO ACTION/RESTRICT and self-parent check. Root siblings must share the ordering uniqueness rule: use PostgreSQL `UNIQUE NULLS NOT DISTINCT (parent_id, display_order) DEFERRABLE INITIALLY DEFERRED`. This permits atomic swaps/compaction; validate final uniqueness at transaction commit. Keep this DDL in Flyway because JPA annotations alone do not express it. Add parent/order indexes and only demonstrated filter indexes.
- Update architecture tests for JPA dependencies confined to persistence/config and domain/application independent of adapters. Existing shared advice test must permit imports of business exception types while still rejecting use-case execution or repository dependencies.

## Transaction and concurrency design

### Category optimistic locking

Metadata updates require expectedVersion; move/delete also require target expectedVersion. Load the current entity in the transaction, compare the request version, apply approved changes, and let Hibernate's version predicate detect a later concurrent edit. Use version-aware entity removal for delete, not a bulk delete that skips version checks. Flush before preparing a success result; transaction commit must succeed before HTTP success is returned. Map optimistic failures, including flush/commit-time wrappers, to 409 `CATEGORY_VERSION_CONFLICT`.

Update only rows whose stored values actually change. Set one command actor/time on those rows; managed-entity dirty checking updates version. Avoid multiple flushes of the same changed row, timestamp callbacks that manufacture changes on no-ops, and bulk Category updates that bypass version/audit checks. A normalized no-op with current preconditions does not update audit/version/revision. Stale preconditions observed at validation still fail; a no-op may linearize at its consistent validation read rather than reserving the state against all future writes.

### Optimistic structure revision

One structure revision for this single-shop tree is sufficient. It is a conflict token, not a pessimistic guard acquired before reading. Metadata-only writes neither acquire nor increment it. Every successful create/delete/parent change/order change must participate, including creates that have no client revision.

For a structural mutation in one short READ COMMITTED transaction:

1. Read structure revision R **before** reading the hierarchy/sibling sets on which validation depends. Move/reorder/delete compare R to the supplied expectedStructureRevision. Do not mutate managed Category entities yet, so JPA cannot auto-flush writes before concurrency validation.
2. Read required nodes/ancestors/descendants/siblings; validate entity versions, exact sibling set, parent existence, cycles and subtree depth; compute changed values. For genuine no-ops return a coherent current view without incrementing revision. If validation reads straddle a concurrent commit, reject conservatively or retry the entire create; never persist an unvalidated plan.
3. Atomically compare-and-set revision from R to R+1 with a conditional update (`WHERE revision = :R`) through the JPA persistence adapter. Inspect the affected-row count. Zero rows means the hierarchy read is obsolete: roll back the entire transaction; do not continue with the old validation. Do not load a managed structure-state entity and then leave its persistence context stale after a bulk CAS; use a revision projection for this path. Do not auto-clear a context containing Category changes.
4. Only after CAS succeeds, apply/persist/remove managed Category entities and changed sibling positions; flush and commit everything together. A Category `@Version` failure, FK/unique failure or any later exception also rolls back the revision increment. Return committed versions and revision, not guessed pre-flush values.

Normal DB row locks taken by the conditional UPDATE during commit are inherent to writes; there is no explicit global pessimistic lock or lock-before-validation. Keep work after CAS small. All structural writers must use this path; a raw repository call bypassing it would invalidate the guarantee.

Why this protects the tree: competing moves validated at the same R cannot both commit their revision transition. After a winner, a loser must discard its old cycle/depth/sibling calculation. All committed structural reads therefore correspond to the revision against which their write succeeded. Category versions separately catch metadata/structural overlap on the same row. The deferrable uniqueness constraint independently prevents duplicate sibling positions.

Create retries structural contention automatically with a fresh transaction and persistence context, re-reading the parent, subtree/ancestors and append position. Use at most 3 total attempts, each fully rolled back before the next. Allocate one UUID for that server request; never repeat remote calls inside retry transactions. Exhaustion returns retryable 409 `CATEGORY_STRUCTURE_CONFLICT` without requiring the client to obtain/send a revision. A concurrently deleted parent returns CATEGORY_NOT_FOUND. Slug collisions and invalid input are not structure retries. This is internal concurrency retry, not create-request idempotency after a lost HTTP response.

Move/reorder/delete with a client revision do not silently retry against a new revision: return 409 `CATEGORY_STRUCTURE_CONFLICT` and require refetch. No-op revision checks use a consistent read and perform no hidden increment.

### Tree and read behavior

- Root level 1, maxDepth configurable/default 8, multiple roots. Reject self-parent, descendant parent and missing parent. On move validate destination depth plus full subtree height; descendants retain their stored flags and unchanged audit/version.
- Dense zero-based sibling order. Create and cross-parent move append; compact the old sibling group after move/delete. Reorder uses the full exact sibling ID list, including root siblings: reject duplicates, missing IDs and IDs from another parent. Audit/version changes only for rows with actual stored changes. Same-parent move is a no-op; explicit reorder handles order changes.
- Compute effective visibility from self and every ancestor being ACTIVE and PUBLIC. Admin reads include hidden/inactive nodes; do not change descendant flags or Product publication.
- Complete flat structure snapshot includes parentId/displayOrder, computed depth/effective visibility, entity versions/audit and structureRevision. Use a read-only REPEATABLE READ transaction for revision plus rows; no N+1 lazy traversal or hidden truncation. Snapshot revision describes topology/order only; it is not a cache token for metadata changes.
- Paged list: page 0, size 20/max 100, literal case-insensitive name/slug search, stored status/visibility filters, direct parentId, rootsOnly and rootId subtree (including selected root). Reject incompatible parent/root selectors. Fixed order createdAt DESC, id ASC; tree order comes from structure snapshot. Parameterize queries and escape LIKE wildcards.
- Detect a configured maxDepth below existing tree depth and report invalid configuration/data explicitly; never silently rewrite the tree.

Technical references: [PostgreSQL 18 transaction isolation](https://www.postgresql.org/docs/18/transaction-iso.html) explains conditional update behavior under concurrent commits; the [Hibernate guide](https://docs.hibernate.org/orm/7.2/introduction/html_single/) describes optimistic version predicates. The chosen CAS algorithm above remains a design to prove with the actual Boot-managed Hibernate and PostgreSQL integration tests.

## Slug, scalar and audit contract

Keep the prior review defaults unless adjusted during contract review: trimmed name 1..200 characters; nullable plain-text description max 4000, blank normalized to null; slug 1..200 matching `[a-z0-9]+(?:-[a-z0-9]+)*`; create defaults INACTIVE/PRIVATE. Define consistent Unicode length validation in Java/DB/OpenAPI.

Backend slug suggestion decomposes accents, handles Vietnamese đ/Đ explicitly, lowercases with Locale.ROOT, collapses separators and bounds output without a trailing hyphen. Return an editable candidate, not a uniqueness reservation. Empty derived candidates require a valid manual slug. Create may derive an omitted slug; manual slugs are validated. Unique collisions return 409 CATEGORY_SLUG_CONFLICT with no automatic suffix. Rename/move retain slug unless the admin explicitly edits it. Hard-delete slug reuse remains allowed; redirect history is outside scope.

IDs, createdAt/createdBy/updatedAt/updatedBy are server-managed. Actor is the validated JWT subject. Return UUID actor references; no synchronous Identity name lookup. Reject/read-ignore non-writable audit fields according to the finalized DTO contract and test spoof attempts. Never serialize JPA entities as API DTOs.

## Media policy and image edits — resolved

The durable policy is in [Media Service: consumer references](../docs/03-architecture/media-service.md#consumer-image-references). Category stores imageAssetId without an FK to Media or a promise that the asset remains available. It never stores provider identity/URLs or deletes bytes on replace/remove/Category delete. No Media service code or schema change is needed for this policy.

For the simplest Issue #23 implementation, persist a syntactically valid UUID without adding a mandatory remote READY lookup. This is permitted by the approved optional-check policy and avoids adding inter-service credentials/scopes or availability coupling. Authorization to edit the Category remains mandatory. Unknown/deleted IDs can therefore be stored; consumers resolve through authorized Media access and display a placeholder for missing, unavailable or failed image loads. The Category API returns the reference even when Media is unavailable. This plan does not add that future frontend UI.

Image edit commands are KEEP | SET(imageAssetId) | REMOVE; omitted edit command means KEEP; create supports absent image or SET. Reject invalid action/ID combinations. SET to the existing ID and REMOVE of an absent reference are no-ops. Unrelated edits retain the reference. Missing media never makes Category reads fail or silently clears imageAssetId.

If a READY preflight is introduced in a later scoped change, it must use a genuinely Media-authorized token, execute outside the Category DB transaction and recheck Category versions before save. READY is only an observation; concurrent/later deletion remains valid and requires no compensation. Do not add a generic client or token flow solely to exercise this optional check now.

## P3 Product reference enforcement

Restrict hard deletion when children exist, with application validation and the self-FK as defense. No cascade. Before P3 enables Product assignments, its migration must add a real Product/association-table Category FK with RESTRICT/NO ACTION in the same Ecommerce database. It protects assignment-versus-delete races; map recognized constraints to CATEGORY_IN_USE. Do not introduce fake Product tables, placeholder reference-check services or claim existing Product integration. The loose Media reference policy does not weaken the future Product-to-Category FK requirement.

## HTTP and authorization contract to implement

Add `contracts/openapi/ecommerce.yaml` using common Problem schemas. Direct base `/api/admin/categories`; existing BFF prefix `/api/ecommerce/admin/categories` already forwards correctly.

| Method/path | Request and response | Permission |
| --- | --- | --- |
| POST base | name, optional slug/description/image, parentId, status/visibility; **no expectedStructureRevision**; 201 + Location + category/revision | category:write |
| GET base | bounded filters/page; page envelope | category:read |
| GET /{id} | admin view with imageAssetId, entity version/audit and current structure revision | category:read |
| GET /structure | complete consistent flat snapshot | category:read |
| GET /slug-suggestion?name= | editable suggestion, no reservation | category:read |
| PUT /{id} | editable scalars + expectedVersion, optional image action; parent/order/audit excluded | category:write |
| POST /{id}/move | nullable destination parentId + expectedVersion + expectedStructureRevision | category:write |
| PUT /sibling-order | nullable parentId + full orderedIds + expectedStructureRevision | category:write |
| DELETE /{id} | expectedVersion and expectedStructureRevision query parameters; 204 | category:delete |

Finalize nullable/missing field rules in DTOs/OpenAPI: PUT requires editable scalar fields, omitted image means KEEP. Missing/invalid required preconditions produce 400. Structural command responses return the new revision and direct clients to refetch snapshot rather than returning the whole tree. Use no-store on admin responses. Authorization precedes resource lookup.

Require ecommerce.api scope and exact Category permission, retaining issuer/audience/RS256 checks and deny-all fallback. Add Identity V7 category:read/write/delete for ecommerce-api and grant to SUPER_ADMIN; no changes to JWT format, token TTLs, existing Media scopes or other services' persistence technology.

Errors: CATEGORY_NOT_FOUND 404; CATEGORY_VERSION_CONFLICT, CATEGORY_STRUCTURE_CONFLICT, CATEGORY_SLUG_CONFLICT, CATEGORY_IN_USE 409; INVALID_CATEGORY_HIERARCHY and CATEGORY_DEPTH_EXCEEDED 400; standard structured validation errors 400. Translate recognized ORM/constraint failures only, preserving rollback. No SQL, Java exception names or raw constraint diagnostics in responses. Missing Media is not a Category error under the chosen local-reference path.

## Implementation phases for Luna ($cr-implement)

Each phase is a reviewable commit group; commit/branch/publication actions still need their normal authorization. This planning update does not implement the phases.

1. **JPA foundation and migration:** update Ecommerce dependencies/config, add V2, CategoryEntity with @Version, revision state and persistence mapping. Keep V1 unchanged. Verify Flyway migration/validation, null-root ordering uniqueness, managed version initialization and rollback; keep foundation tests passing.
2. **Domain and scalar commands:** implement slug suggestion/validation, create/read/update models and use cases, image KEEP/SET/REMOVE as local UUID reference, server-managed audit. Add optimistic Category update/no-op tests; no Media adapter is needed.
3. **Structure concurrency and operations:** implement revision CAS, create retry boundary with fresh transactions, full tree validation, append/compaction, move/reorder/delete and consistent snapshot. Create and all structural writes must use this path before exposure. Add adversarial multi-connection tests before HTTP work; no pessimistic fallback.
4. **Admin API and Identity permissions:** controllers/transport DTOs, exact security rules, V7 permissions, error translation and OpenAPI. Implement slug-suggestion route and create without revision. Verify real security chain, actual Identity-issued permissions and BFF-compatible route paths.
5. **Queries and regression coverage:** bounded filtered list, ancestor visibility, complete consistent snapshot, version/audit responses, query-count sanity and contracts matching actual DTOs. Validate concurrency cases below and commit-time failure mapping; avoid expanding schema/query frameworks.
6. **Documentation, CI and review:** update Ecommerce README from planned JPA to implemented behavior only after code exists; add focused durable Category docs/API links and contract inventory, update Ecommerce CI path filters for its new spec, then run applicable verifies and cr-review against phase target. Media docs updated by this planning pass already define the approved independent-reference policy. No Media Library, attachment infrastructure or unrelated refactor.

## Required verification during implementation

- Domain: depth 8/9, full subtree moves, self/cycle/missing parent, independent status/visibility and ancestor effective visibility, Vietnamese suggestions/manual slugs/rename retention, explicit image edits and no-ops.
- PostgreSQL + actual Hibernate: concurrent metadata edits yield one version conflict; versions/audit reflect each actual row change; no-op leaves values unchanged; reordered siblings obey deferred uniqueness at commit; stale parent/row deletion and rollback do not leak revision increments.
- Structural races with separate connections and deterministic latches/barriers: opposite moves that would form a cycle; overlapping subtree moves/depth changes; create/create without client revision; create/reorder; create/delete-parent; move/reorder; delete/reorder; metadata/structural overlap on changed rows. Assert final acyclic bounded-depth tree, dense unique sibling order and correct versions/audit, not only response codes.
- Create retry: force one CAS failure then success; verify fresh persistence context/revalidation, no duplicate insert and append position recalculated. Force exhaustion and verify bounded retryable conflict with no partial rows. Client-revision commands never overwrite a newer structure automatically.
- Snapshot: revision and rows from one snapshot under concurrent commits, includes roots/hidden nodes, deterministic order and no N+1. Unique slug collision is recognized independently of revision/entity conflicts.
- HTTP/security: 401/403, wrong audience/scope and roles-only rejection, spoofed audit, malformed/field/business failures, missing/stale preconditions, create without expectedStructureRevision, complete sibling-list validation, correctly mapped flush/commit-time optimistic and deferred-constraint failures.
- Media boundary: Category persists/returns imageAssetId independently of Media availability; unrelated edits retain it; replace/remove/delete never call Media DELETE; unknown/deleted references do not break Category APIs. Placeholder is a requirement for the separate consumer UI CR, not proof of UI implemented here.
- Flyway/JPA: existing V1-to-V2 upgrade, schema validation and rollback, real version-aware deletion, domain/adapter boundaries, OpenAPI/common-ref parser, existing packaged Identity flow. No mocks counted as proof of concurrency safety.
- Java 25, Docker and UTC as documented: build Identity with `.\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package`; run Ecommerce `.\services\ecommerce\mvnw.cmd -f services/ecommerce/pom.xml -B --no-transfer-progress verify`; full Identity verify for V7; git diff --check. Restore prior JAVA_TOOL_OPTIONS. No Media/BFF implementation change is required by this plan.

## Rollout, review status and remaining detail

Apply additive Identity permissions and Ecommerce Flyway migrations; issue fresh tokens for new grants. Keep Hibernate schema generation disabled, back up business data and retain migrations on rollback. P3 Product FK enforcement remains an explicit future prerequisite. No distributed transaction, reference recovery job or Media attachment deployment is needed.

The nine decisions in the 2026-11-13 request are settled and take precedence over contradictory recommendations in the earlier plan. Field lengths/defaults/filter details and error precedence above remain review defaults; adjust them in this plan/contract review without reopening the approved persistence/concurrency/Media direction. The optional READY check is not an implementation blocker: this plan selects local reference persistence for Issue #23.

Keep this plan at `.temps/23-plan.md` as requested. Planning verification consists of source/doc inspection, relative-link/Markdown checks and diff review. No application code, OpenAPI, dependency, config, migration or tests are changed by this documentation update; no Maven/CI results are claimed.
