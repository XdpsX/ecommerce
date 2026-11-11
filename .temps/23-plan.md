# Issue #23 — Category Management Backend

Date: 2026-11-10. Status: proposed implementation plan; awaiting scope/policy approval. No production changes authorized by this planning artifact.

Issue: https://github.com/XdpsX/ecommerce/issues/23

## Recommended scope

Deliver Category backend in Ecommerce with PostgreSQL/JDBC, adjacency-list hierarchy, explicit commands and query models, permission-based authorization and an OpenAPI contract. Keep the first implementation deliberately serialized at the Category write boundary; use optimistic versions to report conflicts to clients rather than silently overwrite.

Ecommerce owns categories and image references; Identity owns actor identities and entitlements; Media owns asset availability/deletion. Backoffice UI is a separate CR. Do not implement Product, storefront routes, redirects, event infrastructure, generic tree engines or cross-service database access.

**Important dependency:** Media has no attachment/deletion-protection API. A metadata GET before saving an image reference cannot prevent concurrent or later asset deletion. Do not present that as a safe attachment contract or mark the complete issue done without an explicit decision below.

## Repository and branch evidence

- Working branch: `feat/23-category-management-backend-draft`; clean tracked working tree at inspection.
- HEAD: `f6126d16f162056203d9b02ea8a81ab4ac79f38b`.
- Target: `phase/2-catalog-management`. Local branch, remote-tracking branch and live GitHub ref all point to the same SHA. `git rev-list --left-right --count HEAD...origin/phase/2-catalog-management` returned `0 0`.
- Target already contains P1 via merged PR #22. No fetch, branch mutation or synchronization performed. Recheck divergence before implementation; do not automatically merge/rebase.
- Issue #23 is open in M1, has no comments at inspection, explicitly limits this CR to backend. The phase document remains a proposal; its stale skeleton descriptions do not override current source.

Source anchors, relative to repository root:

| Evidence | Consequence |
| --- | --- |
| `services/ecommerce/README.md`, `pom.xml`, `src/main/resources/db/migration/V1__ecommerce_foundation.sql` | JDBC/Flyway/Bean Validation already available; V1 is a foundation migration only. Add V2; never rewrite V1 |
| `services/ecommerce/src/main/java/com/ecommerce/api/config/ResourceServerSecurityConfiguration.java` | UUID subject, RS256/issuer/audience/time validation, `PERM_` and `SCOPE_` mapping, deny unknown routes. No Category routes exist |
| `services/ecommerce/src/main/java/com/ecommerce/api/shared/error/{ApiProblems,GlobalExceptionHandler}.java` | Existing shared Problem Details and MVC/security error paths; extend business mappings, preserve security semantics |
| `services/ecommerce/src/test/java/com/ecommerce/api/{ArchitectureBoundaryTest,PersistenceFoundationIT}.java` | Source-boundary checks and PostgreSQL 18.6 migration/transaction baseline exist |
| `services/identity/src/main/resources/db/migration/{V2__identity_admin_guard_and_catalog,V6__media_entitlements}.sql` | Current catalog contains Identity and Media permissions, no Category permissions; add Identity V7 rather than modifying existing migrations |
| `services/identity/src/main/java/com/ecommerce/identity/authorization/adapter/oauth2/token/AccessTokenCustomizer.java` | `ecommerce.api` selects `ecommerce-api` audience; permissions filtered by audience. No JWT changes needed |
| `services/identity/src/main/java/com/ecommerce/identity/admin/adapter/persistence/JdbcAdministratorRepository.java` | Existing singleton guard-row locking is a local precedent; actor accounts stay in Identity |
| `services/backoffice-bff/src/main/java/com/ecommerce/backoffice/config/GatewayConfiguration.java`, `src/main/resources/application.yaml` | `/api/ecommerce/**` already relays tokens to `/api/**`; current BFF requests `ecommerce.api`, not `media.api`. No new Category gateway route needed |
| `contracts/openapi/media.yaml`, Media `asset/adapter/web/MediaAssetController.java`, `asset/application/service/MediaAssetService.java` | Metadata GET requires media scope/audience/read permission, returns only READY; deletion is permitted without reference checks. There is no atomic attach/reserve/release contract |
| `docs/03-architecture/media-service.md` | Explicitly requires consumers to establish deletion protection before durable references; valid unreferenced READY media retained |
| `docs/06-development/code-organization.md`, `docs/04-contracts/http-errors.md`, ADR-0001/0002 | Feature ownership; root config and service-local shared errors; permission authorization; string detail plus structured validation errors |
| `.github/workflows/ecommerce-ci.yml` | Builds Identity then verifies Ecommerce; add new Ecommerce OpenAPI path trigger |

No legacy code is required to invent these rules: Issue #23 supplies the business baseline. Legacy repositories were not inspected or copied in this planning pass.

## Proposed package layout

Use `com.ecommerce.api.category` as the present capability; do not introduce an otherwise-empty catalog wrapper. Future Brand/Product packages can be added on demonstrated boundaries.

```text
com.ecommerce.api
  EcommerceApplication
  config/
    ResourceServerSecurityConfiguration                  existing, add exact route rules
    CategoryConfiguration, CategoryProperties            new composition + validated settings
  shared/error/
    ApiProblems, GlobalExceptionHandler                   existing, extend mappings
  category/
    domain/
      model/Category, CategoryStatus, CategoryVisibility
      service/CategoryHierarchyPolicy, CategorySlugPolicy
      exception/                                        invariant errors as needed
    application/
      service/CategoryCommandService, CategoryQueryService
      model/                                            commands, views, page/snapshot, immutable settings
      port/CategoryRepository, CategoryQueries
      exception/                                        not-found, stale version/revision, resource-in-use
    adapter/
      web/CategoryController, CategoryStructureController  transport DTOs beside their owner
      persistence/JdbcCategoryRepository, JdbcCategoryQueries
      client/                                           only if approved Media integration needs it
```

Names are illustrative, not a demand for one interface/class per operation. Keep SQL in persistence, HTTP/JWT in web, transaction orchestration in application, invariants independent of Spring. Group related exceptions/models/ports instead of a flat application folder. Application/domain do not import config/shared or vendor clients. No empty packages or universal aggregate spanning the whole tree.

Current `ArchitectureBoundaryTest` forbids domain/application imports from all shared classes, whereas the convention permits advice to map business exceptions. Narrow that assertion for the advice's error-type dependencies; continue forbidding use-case execution, repositories and config imports. Extend meaningful layer checks for the new feature rather than weakening them globally.

## Proposed behavioral decisions — require approval

These recommendations supplement the issue; they are not already-approved business rules.

### A. Scalar validation and slugs

- UUIDs generated server-side; UTC `Instant` audit timestamps; actors from validated JWT subject, never request body/header. Return actor UUIDs only, no synchronous Identity lookup for names. Identity display enrichment is separate consumer work.
- Name trimmed, 1..200 characters; description plain text, nullable, at most 4000 characters; slug 1..200 characters matching `[a-z0-9]+(?:-[a-z0-9]+)*`. Blank description normalizes to null. Define the exact length-counting rule consistently in contract, Java and DB tests.
- Create defaults: INACTIVE, PRIVATE (safe initial state). Explicit values allowed. New category appends to the end of its parent/root group.
- Slug suggestion uses Unicode decomposition with combining-mark removal, explicit Vietnamese đ/Đ mapping, lowercase with Locale.ROOT, separator collapse and trim. Bound output without leaving trailing separators. If nothing remains, require an explicit valid slug rather than inventing an ID-based name.
- Create with omitted slug derives a suggestion; customized slug is validated, not silently rewritten. Globally unique DB constraint; collision gives 409 `CATEGORY_SLUG_CONFLICT`, no automatic numeric suffix. Existing slug changes only on explicit edit; name/move never regenerates it. Hard delete permits slug reuse; redirects/history remain out of scope.

### B. Write serialization, versions and snapshot

- One persisted `category_structure_state` singleton with BIGINT revision. Every Category write acquires its row first, then affected category rows in UUID order. This includes metadata edits: it simplifies interaction with deletion and structural row-version/audit updates in V1. Reads do not take write locks. Local DB operations only while holding locks; no network I/O inside transactions.
- Metadata update requires `expectedVersion`. Create requires `expectedStructureRevision`; move/reorder/delete require structure revision, and move/delete also require target entity version. Missing/invalid tokens: 400; stale tokens: 409 with stable codes. Validate authorization before looking up resources; document missing-versus-stale precedence.
- All changed category rows increment entity version once and set the same transaction audit actor/time. Creation initializes version/audit. Structural operations increment structure revision once. Metadata changes do not increment structure revision. Exact no-op after precondition checks changes no version, revision or audit. Stale no-ops still conflict.
- Store sibling positions as dense zero-based integers, including roots; sort deterministically by position then UUID. Reorder supplies the exact current sibling ID set in final order, rejecting duplicates, missing/foreign children. Cross-parent moves append to destination; reorder is a separate operation. This avoids inventing index-insertion semantics.
- Move changes parent and any necessary positions atomically. Reject missing parent, self/descendant parent, or `destinationDepth + subtreeHeight > maxDepth`; roots have depth 1, default maxDepth 8. Do not store derived depth/effective visibility unless justified by measurements.
- Compact old sibling group after move/delete; version/audit only rows whose stored fields actually change. Descendants with unchanged stored fields retain audit/version; their computed depth/effective visibility may change.
- Snapshot: complete flat node list with parentId/position, computed depth/effective visibility, per-row versions/audit and structureRevision. Use one repeatable-read, read-only transaction for revision + rows, or one consistent SQL statement. Revision represents topology/order, not a cache validator for all metadata. No hidden truncation/pagination on the structure endpoint.
- Lowering configured maxDepth below existing tree depth must be detected explicitly during startup/config validation and reported; do not silently rewrite data. Complete snapshots have cost proportional to category count; monitor before introducing limits or partial-tree protocols.

### C. Read contract and concurrency-safe schema

- New Ecommerce V2: `categories` plus singleton guard state. Columns: issue fields, BIGINT version, TIMESTAMPTZ audit, UUID actor IDs with no Identity FK, nullable media UUID with no cross-service FK. Self-parent FK uses RESTRICT/NO ACTION; no cascade.
- DB checks: slug format/uniqueness, nonblank bounded name, enum values, positive version, nonnegative sibling position, self-parent rejection. Sibling uniqueness must treat NULL parents as one group. Use PostgreSQL `UNIQUE NULLS NOT DISTINCT (parent_id, display_order)` with a deferrable constraint if needed for atomic permutation/compaction; test real constraint behavior. Index parent/order; add filter/search indexes only for actual SQL, not every possible combination.
- List: zero-based page default 0, size 20/max 100; literal case-insensitive name/slug substring search with escaped SQL wildcard characters; enum filters, direct parentId filter, rootsOnly and rootId subtree filter. Incompatible parent/root selectors produce 400. Include selected root itself in rootId subtree. Filter stored status/visibility; expose effective visibility separately. Stable fixed list order `createdAt DESC, id ASC`; tree order belongs to snapshot. Do not promise sibling contiguity across filtered pages.
- Effective visibility is true only if every node in self+ancestor chain is ACTIVE and PUBLIC. Admin queries return inactive/private nodes; no descendant status mutation or Product side effect.
- All administrative responses use no-store. Reject unknown sort fields rather than interpolate SQL. Parameterize queries and bound pagination/search inputs. No silent truncated snapshot; operational limits beyond initial field/page bounds require an explicit contract decision.

### D. P3 reference enforcement

- Reject deletion with any child under the structure guard; DB self-FK independently protects the invariant.
- There are no Product rows or assignment flows now. Do not add fake Product tables or an always-false reference checker claiming Product protection.
- P3 must add Product-to-Category FK with RESTRICT/NO ACTION in the Ecommerce database before enabling assignments. If a join table is selected, FK must protect its Category reference too. Assignment insertion and Category deletion then race safely through real PostgreSQL FK locking. Catch only recognized integrity constraints and return `CATEGORY_IN_USE`.
- Current issue can demonstrate children protection and document the P3 schema prerequisite; it cannot honestly test real Product references before Product exists. A test-only referencing table can validate FK delete-conflict mapping, clearly labeled infrastructure evidence, not implemented Product integration.

### E. Media integration — blocking policy/scope decision

Existing Media GET proves availability only at observation time; it is neither attachment authorization nor deletion protection. Existing BFF token does not have Media audience/scope; never forward an Ecommerce-only token and assume it is valid. Never use Cloudinary directly or persist signed delivery URLs.

**Recommended:** establish a separately approved, minimal Category–Media reference contract before enabling persisted images. Its design must specify consumer authentication (not reuse arbitrary admin tokens), authorized eligibility, idempotent reference reservation/release, delete-versus-reservation serialization in Media, retries after ambiguous outcomes and compensation/reconciliation without cross-service transactions. This is a real ownership/consistency extension, not an incidental HTTP client addition. Do not implement a generic attachment framework as part of Category by assumption.

Alternative requiring explicit approval: use READY metadata validation with `media:read` on a properly scoped user token, accept dangling references from concurrent/later deletes, represent unavailable images safely and revise the documented deletion-protection requirement. This is weaker than current durable architecture; do not silently pick it to finish the issue.

Core phases below can proceed after core approval, but cannot satisfy the image acceptance criterion until this decision is settled. Deferring images means Issue #23 remains partially complete or its scope must be explicitly revised.

For either approved contract: image command is explicit `KEEP | SET(mediaId) | REMOVE`; omitted image command on edit means KEEP, create allows no image/SET. Reject impossible action/id combinations. Unrelated edits never trigger image removal or Media availability calls. Replacing/removing/deleting a Category never requests byte deletion. New SET failures leave the old reference/category untouched; retries and late responses must recheck the entity version. Same-id SET is a no-op once the contract's validation semantics are defined. No provider URL in Category persistence.

## Proposed HTTP and authorization contract

Add `contracts/openapi/ecommerce.yaml`, reference existing common Problem schemas. Direct service prefix `/api/admin/categories`; BFF equivalent `/api/ecommerce/admin/categories` already routes correctly.

| Method/path | Input / behavior | Permission |
| --- | --- | --- |
| POST base | name, optional slug/description/image, parentId, status/visibility, expectedStructureRevision; 201 + Location + category/current revision | category:write |
| GET base | page/size/search/rootsOnly/parentId/rootId/status/visibility; page envelope | category:read |
| GET /{id} | full admin view + current structure revision | category:read |
| GET /structure | complete consistent flat snapshot; includes hidden/inactive nodes | category:read |
| GET /slug-suggestion?name= | normalized candidate, not uniqueness reservation; reject empty/invalid input | category:read |
| PUT /{id} | full scalar edit + expectedVersion, optional explicit image action; excludes parent/order/audit | category:write |
| POST /{id}/move | parentId nullable for root, expectedVersion + expectedStructureRevision | category:write |
| PUT /sibling-order | parentId nullable + exact orderedIds + expectedStructureRevision | category:write |
| DELETE /{id} | required expectedVersion and expectedStructureRevision query parameters; 204, no cascade | category:delete |

PUT must clearly specify nullable fields and required editable scalars; omitted image means KEEP by explicit contract. Missing required scalar is validation error, not accidental clearing. Declare literal routes ahead of UUID path parsing. Structural command responses include resulting structureRevision and affected row IDs/versions, or direct clients to refetch snapshot; prefer the latter to avoid oversized mutation payloads.

Require both `SCOPE_ecommerce.api` and the exact operation permission on Category routes while preserving issuer/audience/RS256 checks and deny-all fallthrough. Roles/scopes alone never grant permissions. Add Identity V7 permissions category:read/write/delete for ecommerce-api and assign to SUPER_ADMIN using existing catalog conventions; do not change JWT claims, privileged-admin protection, token TTLs or client scopes. Existing tokens gain permissions only after new issuance.

Codes: `CATEGORY_NOT_FOUND` 404; `CATEGORY_VERSION_CONFLICT`, `CATEGORY_STRUCTURE_CONFLICT`, `CATEGORY_SLUG_CONFLICT`, `CATEGORY_IN_USE` 409; `INVALID_CATEGORY_HIERARCHY` and `CATEGORY_DEPTH_EXCEEDED` 400; field validation follows common 400/errors format. Media unavailability/auth codes depend on the selected contract, must not leak credentials/provider responses. detail remains string. Do not convert all integrity exceptions into slug collisions.

## Ordered implementation phases for Luna ($cr-implement)

Each phase is a coherent review/commit candidate, not permission to commit automatically. Do not check off incomplete acceptance criteria. Review gates can halt dependent slices without preventing independent approved work.

1. **Approve decisions and contract draft.** Resolve A–E and the table above; record accepted decisions in durable Category docs. Add OpenAPI draft matching approved routes, validation/null/no-op/precondition rules and errors. Parse external common refs in contract tests. No speculative endpoints in published contracts beyond the approved scope.
2. **Domain and additive persistence.** Add category models/policies, V2 schema, JDBC ports/adapters and validated config maxDepth. Tests: slug/Vietnamese normalization, depth/cycle rules, DB checks/uniqueness/root ordering, migrations from V1 and rollback. No JPA/new tree library.
3. **Create/read/metadata use cases.** Implement transaction guard, ID/actor/time/version rules, deterministic paged queries and consistent full snapshot/effective visibility. Add stale/no-op/slug-conflict tests and mixed-visibility ancestor chains. Keep approved Media SET path out until its actual dependency is available; do not implement a success stub.
4. **Move/reorder/delete.** Implement subtree-depth checks, dense ordering, expected revisions, audit/version propagation, children restriction and P3 integration notes. Use multi-connection PostgreSQL tests with barriers/latches, not sleeps, for competing moves/cycle creation, create/reorder, move/delete, stale edits and rollback. Assert final valid tree and one expected conflict, not only HTTP codes.
5. **Security and web integration.** Add exact routes/DTOs/controllers and business error mapping; Identity V7 entitlement seed; real-chain HTTP tests; extend architecture checks to match approved dependency rules. Preserve security probes as test-only. Verify identity-issued token can exercise Category permissions through the existing audience/scope path.
6. **Image integration after E is resolved.** Implement only the approved contract via application port and HTTP adapter/config. Enforce keep/set/remove, credentials/token scope, bounded timeout, no network call under DB locks and version rechecks. Verify deleted/not-ready/forbidden/missing/timeout/ambiguous responses and concurrent reference/deletion semantics. If prerequisite is separate CR, stop this slice and report incomplete issue scope explicitly.
7. **Documentation, CI and final review.** Update Ecommerce README, architecture index and a focused Category document; Identity permission docs and Media contracts/docs only if actually changed/approved. Link Product FK prerequisite and image limitations. Update contract inventory and Ecommerce CI paths for ecommerce.yaml. Run applicable verifies; final read-only cr-review against phase target, including all migrations, permissions and package boundaries. Do not update milestone live progress or rewrite ADR statuses incidentally.

## Verification and rollout

- UT: invariants, Vietnamese slug edge cases/empty/collision behavior, original slug retention, independent flags, explicit image action parsing and genuine no-op detection.
- PostgreSQL IT: depth 8 accepted/9 rejected including moving an entire subtree; opposite moves cannot create cycle; multiple roots and concurrent append; whole-sibling reorder/set validation; stale entity/revision conflicts; snapshot/revision consistency; unique slug race; children FK; rollback and audit/version of every actually changed row. Metadata-versus-structure races and no-op stale requests are explicit cases.
- HTTP/security IT: no credentials 401, wrong/missing permission or scope 403, roles-only rejected, invalid issuer/audience/subject rejected, read-only admin vs writes, forbidden before resource probing, malformed/field/business errors, null/missing image semantics, audit fields cannot be spoofed, page/filter bounds and deterministic results.
- Contracts: parse new spec with common refs; compare methods/routes/request requirements/enums/response schemas/Problem codes against controllers. Keep packaged Identity flow and architecture tests green. Do not count mocks as proof of distributed deletion protection.
- Media slice needs contract-level tests and real-service/state concurrency tests proportional to the chosen solution. No Cloudinary configuration experiments are necessary for Category; keep existing direct/multipart behavior unchanged.
- Commands from root, Java 25 + Docker, UTC for Maven/child JVMs as documented: build Identity with `.\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package`, then `.\services\ecommerce\mvnw.cmd -f services/ecommerce/pom.xml -B --no-transfer-progress verify`; run full Identity verify for entitlement changes, Media verify only when its implementation changes, BFF verify if its configuration actually changes. Finish `git diff --check`. Preserve prior JAVA_TOOL_OPTIONS after verification.
- Deploy additive Identity permissions before testing newly issued tokens; deploy Ecommerce migrations before enabling routes. Re-login where needed; old JWTs are still valid but lack new grants. Do not add browser bearer-token storage or silently expand BFF Media scopes. Separate image prerequisite deployment is a release dependency.
- Schema rollback is operational: older Ecommerce ignores additive tables but do not drop Category data automatically. Backup and migration checks before production. No blanket promise of zero-downtime rollout or untested production behavior.

## Approval handoff

Approve/adjust the proposed validation/default/slug policy, serialization/revision/reorder/API contract, and P3 FK prerequisite. **Choose the Media path explicitly**: recommended prerequisite reference contract versus approved weaker availability-only semantics. Scope increases to Media ownership or authentication need their own concrete approved plan. Nothing in this plan authorizes schema/code implementation, branch synchronization or Git publication.

Planning verification: source/doc/issue/remote-ref inspection only. Maven/CI not run because no application changes were made. Only this ignored plan artifact was written.
