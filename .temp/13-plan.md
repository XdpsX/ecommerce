# CR #13 — Ecommerce backend foundation

Refined plan; application source remains unchanged. Prepared and reviewed 2026-11-06. The scope and architecture below incorporate the user's requested adjustments; implementation has not been authorized.

Recommend completing the missing persistence and executable validation foundations while preserving the existing HTTP/security boundary. Use Spring JDBC, PostgreSQL and Flyway, consistent with Identity and Media. Keep one Maven application and introduce feature packages only when actual business capabilities arrive.

## Scope and integration base

- Request: https://github.com/XdpsX/ecommerce/issues/13, `[API] Establish Ecommerce Backend Foundation`; M1/P1. Issue has no comments at inspection time.
- Acceptance: shared backend/persistence conventions; Identity-protected admin operations; foundation tests and Maven verification.
- Proposed implementation/PR base: `phase/1-core-services-foundation`, confirmed by the user. The issue's `phase/1` reference is outdated and is not a blocker. No branch created or switched; updating GitHub issue metadata is outside this plan edit.
- Inspected branch: `chore/13-ecommerce-backend-foundation-draft`; HEAD and local phase branch both point to `ca6e52e88d2b9c06d7919a82d362fd707a3d016d`. Tracked working tree clean at inspection. Remote branch names checked; remote head freshness was not compared.
- Out of scope: catalog/product/inventory/order features, business tables, production demo endpoints, Identity redesign, BFF/browser sessions, Media/Notification integration, Redis/event brokers, generic CRUD repositories/base entities, Maven module splitting and broad refactoring.

## Current behavior and evidence

Paths below are relative to repository root.

| Evidence | Current behavior / implication |
| --- | --- |
| `services/ecommerce/README.md`; `src/main/java/com/ecommerce/api/adapter/web/ResourceServerSecurityConfiguration.java` | Stateless resource server already validates RS256, issuer, audience, token times and identity claims; maps permissions to `PERM_` and scopes separately; enables method security; defaults to deny all unknown routes. There are no production business endpoints. Preserve this behavior. |
| `services/ecommerce/src/main/java/com/ecommerce/api/adapter/web/{ApiProblems,GlobalExceptionHandler}.java` | RFC 9457 error boundary already exists, including a body-validation handler, safe messages, no-store and protocol headers; MVC advice preserves handling outside `/api`. Extend only demonstrated gaps. |
| `services/ecommerce/pom.xml`; `src/main/resources/application.yaml` | No production datasource, Flyway, JDBC starter or Bean Validation starter. JDBC and PostgreSQL driver are test-only; Testcontainers PostgreSQL support already exists. |
| `services/ecommerce/src/test/java/com/ecommerce/api/adapter/web/ResourceServerSecurityIT.java` | Existing signed-token tests cover allowed permissions, role/scope separation, wrong issuer/audience, cookie-only access and default denial. They currently start a full context without an Ecommerce datasource. |
| `services/ecommerce/src/test/java/com/ecommerce/api/adapter/web/IdentityEcommerceFlowIT.java` | Real packaged Identity producer issues a token through Authorization Code + PKCE; permission changes are exercised using new tokens. Its database belongs to Identity, not Ecommerce. |
| `services/ecommerce/src/test/java/com/ecommerce/api/EcommerceApplicationTests.java` | Empty full-context smoke test currently runs under Surefire. Production datasource auto-configuration will invalidate its database-free assumptions. |
| `services/ecommerce/src/test/java/com/ecommerce/api/ArchitectureBoundaryTest.java` | Existing HTTP placement and persistence separation check; no new architecture framework needed. Its current root-web placement assumption must be revisited when real feature controllers arrive in later CRs. |
| `services/{identity,media}/pom.xml` and `src/main/resources/application.yaml` | Both already use Spring JDBC, Flyway and PostgreSQL. Reuse the approach, not their business schema or runtime classes. |
| `infra/compose/compose.yml`; `infra/compose/init.sql`; `.env.example` | PostgreSQL 18.6; default `POSTGRES_DB=ecommerce`, separate Identity/Media databases. Ecommerce database exists on a fresh default setup, but a customized POSTGRES_DB or existing volume may require explicit provisioning. |
| `.github/workflows/ecommerce-ci.yml` | Java 25; packages Identity, then runs Ecommerce Maven verify including Spotless and Failsafe. Existing relevant-path PR and phase push triggers. |
| `docs/06-development/code-organization.md`; `contracts/README.md` | Feature-first packages, only needed layers; no speculative API schemas. Existing root namespace `com.ecommerce.api` remains. |

Relevant history: `cb2ff45` established the Identity JWT consumer; `c113938` organized Ecommerce HTTP/security adapters; `ca6e52e` integrates Media foundations. The issue's skeleton description predates these foundations; do not reimplement them.

## Contracts and ownership

- Identity produces JWTs/JWKS; Ecommerce consumes the existing issuer/audience/claims contract. Identity owns credentials and entitlements. No direct Identity table access from production Ecommerce.
- Ecommerce owns its datasource and migration history in a separate database. No shared schemas or cross-service foreign keys. Actor IDs can be stable token subjects when future features need them.
- Preserve existing `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI`, `IDENTITY_AUDIENCE` keys and authorization semantics. No new permission catalog entries without real operations.
- REST failures keep `contracts/openapi/common/problem.yaml` and `docs/04-contracts/http-errors.md` semantics. Production successful endpoints are not added, so no speculative `ecommerce.yaml` or generated client.
- Datasource configuration follows Identity and Media: `url: ${ECOMMERCE_DB_URL:jdbc:postgresql://localhost:5432/ecommerce}`, `username: ${ECOMMERCE_DB_USERNAME:ecommerce}`, `password: ${ECOMMERCE_DB_PASSWORD}`, and `spring.flyway.enabled: true`. The password has no fallback. Example local credentials match Compose's `POSTGRES_USER`/`POSTGRES_PASSWORD`; nonlocal credentials are supplied externally.
- Add `application-local.yaml` matching both services: `spring.config.import: "optional:file:../../.env[.properties]"`. Activate `local` explicitly. This path is relative to the application process working directory: Maven `spring-boot:run` defaults to `services/ecommerce`; configure the same directory in the IDE or supply environment variables directly. Plain Java launched from the repository root must not be documented as resolving this path to the root `.env`. Other profiles do not import developer `.env`. Preserve existing Identity keys and the current HTTP port.

## Ordered implementation

1. Use `phase/1-core-services-foundation` as the proposed integration/PR base. Recheck the working tree and base before authorized implementation. Work on the existing branch or another explicitly authorized task branch; this plan edit does not authorize implementation or Git mutations.
2. In `services/ecommerce/pom.xml`, add Boot-managed JDBC, validation and Flyway support plus PostgreSQL Flyway support. Change the existing PostgreSQL driver to runtime scope; remove redundant test-only JDBC declarations when covered by the starter. Follow existing service dependency conventions; do not upgrade framework/tool versions incidentally.
3. Add datasource/Flyway configuration in `src/main/resources/application.yaml` and the explicit `application-local.yaml` import described above. Add example Ecommerce datasource keys to root `.env.example`, using the existing service naming/default conventions. Require the password without a fallback; tests supply isolated configuration and do not activate `local`.
4. Keep `src/main/resources/db/migration/V1__ecommerce_foundation.sql` with comments explaining its purpose and the single SQL statement `SELECT 1;`. This intentional no-op establishes the first versioned migration and verifies the Flyway/PostgreSQL startup path without inventing a business schema. Flyway records V1 in its schema history; this is a normal migration, not a Flyway baseline operation. Future capabilities append V2 and later migrations; do not rewrite V1 after it has been applied. Do not enable `baselineOnMigrate` (`baseline-on-migrate`), clean-on-start or ORM schema generation. Do not introduce artificial business tables.
5. Add a PostgreSQL-backed `PersistenceFoundationIT` in the matching test package. Verify startup migration, recorded V1/checksum and repeat migration without reapplication. Use PostgreSQL 18.6 and a static per-class Testcontainer. For transaction verification, create an ordinary test-owned fixture table in the isolated test database outside the transaction under test, commit a known seed row, and use the application's configured `PlatformTransactionManager` through `TransactionTemplate` with its datasource/JDBC access. In the transaction, update the seed and insert a distinct row, assert those writes are visible, then throw a deliberate test exception. Catch/assert it outside the callback; from a fresh transaction/connection, assert the seed is unchanged and the inserted row is absent. Include a successful committed write as a control, so a fixture that never persists cannot pass. Do not wrap this test in automatic test-managed rollback or rely on temporary-table disappearance/session behavior. Reset deterministic rows between tests and clean up the fixture table independently. Keep all fixture DDL/data under `src/test`; it is not a production migration, entity or repository abstraction.
6. Adapt full-context tests for the new datasource requirement. Replace the empty Surefire context smoke test with the meaningful persistence IT. Give resource-server IT an isolated Ecommerce database. Give the cross-service flow a separate Ecommerce database/container from the existing Identity fixture; never bind the application datasource to the Identity database. Keep pure architecture/error unit tests container-free. Do not disable Flyway globally to make tests pass.
7. Add test-only validation request DTO/controller/configuration, following the existing security-probe pattern. Exercise real `@Valid` MVC processing with the validation provider, safe field errors and malformed input. Keep fixtures under `src/test`, authorize only their exact routes and preserve production deny-all. Preserve existing HTTP errors and API conventions; change `GlobalExceptionHandler` only when a focused failing test demonstrates a concrete gap against the existing error contract. Do not broaden method-validation support or invent domain conflict mappings without such evidence.
8. Preserve the existing JWT resource server, authorization behavior, token/security regression coverage and real Identity flow. Focused expiry/signature/malformed-claim negatives may fill demonstrated coverage gaps using existing fixtures; they do not authorize a security redesign. Change production security only when focused tests demonstrate a concrete contract gap. Production permission gates remain the responsibility of real feature endpoints, with the current test-only probe proving the foundation.
9. Update `services/ecommerce/README.md` with ownership, dependencies, migration lifecycle, configuration, local run and verification commands; link canonical organization/error rules. Update `infra/README.md` for Ecommerce setup and safe existing-volume provisioning if needed. Add explicit idempotent Ecommerce database provisioning to `infra/compose/init.sql` only if required to support the documented custom-POSTGRES_DB case. No volume reset. Do not rewrite ADRs or unrelated documentation.
10. Run the affected Maven verification and inspect the final diff. Existing Ecommerce CI should suffice; update its path filters only if newly relevant external config/contract inputs actually affect verification. Record commands/outcomes and link this issue in eventual handoff.

## Minimal meaningful verification

| Boundary | Required evidence |
| --- | --- |
| Persistence | Fresh real PostgreSQL DB applies V1 `SELECT 1;` and records version/checksum; second migrate applies nothing. Ordinary test-only table and committed seed establish that writes occur, deliberate failure rolls back update/insert, and a successful control commits. Verify outcomes outside the transaction under test. No production business tables or Identity tables/migrations in Ecommerce DB. |
| Validation/errors | Valid test request succeeds; multiple invalid body fields return 400/VALIDATION_FAILED and correct pointers; malformed JSON returns safe BAD_REQUEST; content type, status, no-store and lack of rejected values/parser/SQL diagnostics checked. Existing 405/Allow and non-API handling remain covered. |
| Authorization | Existing signature/issuer/audience/permission and Identity code+PKCE suites pass with datasource enabled. Missing credentials yield 401/challenge, insufficient permissions 403, proper permission success. No role/scope bypass; unknown production routes remain denied. |
| Build/structure | Container-free UT and existing architecture check; Failsafe discovers and executes new/existing IT; Spotless check and packaging pass. Verify test-only endpoints are absent from the production JAR. |

Planned commands from repository root (Windows PowerShell):

```powershell
& .\services\identity\mvnw.cmd -f services/identity/pom.xml -B --no-transfer-progress -DskipTests package
& .\services\ecommerce\mvnw.cmd -f services/ecommerce/pom.xml -B --no-transfer-progress verify
```

Prerequisites: Java 25, Maven dependencies available and Docker running. CI uses the corresponding `./mvnw` commands. Container-per-class fixtures must be deterministic and must not require developer `.env` credentials. For changed Markdown, verify local links and example commands/configuration against source. No Maven or runtime tests were run during this read-only planning pass; passing execution is an implementation acceptance gate.

## Risks, rollout and decisions

- Adding datasource auto-configuration changes startup requirements: provision the Ecommerce DB/account and supply credentials before deploying the new application. Fresh local Compose uses the existing default DB; existing/custom environments need explicit non-destructive provisioning. Never delete volumes or auto-baseline an unknown nonempty database.
- The user's refinement retains the JDBC/PostgreSQL/Flyway architecture and explicitly confirms V1 `SELECT 1;` without business tables or `baselineOnMigrate`. These are settled planning choices, not outstanding approval blockers. No generic repository abstraction is needed.
- No business data migration or token/REST contract changes are intended. Identity producer needs no release change; deploy database/configuration before Ecommerce and retain the existing Identity test prerequisite. The added Flyway history can remain if rolling back this foundation release.
- Reusing Identity's test DB for Ecommerce would silently violate ownership and migration boundaries; explicitly separate fixtures. Extra containers increase CI time; stay within the existing 25-minute job budget and optimize only if measured.
- Test-only authorized routes must not enter production sources. Bean Validation errors must not expose rejected values or untrusted custom messages.
- Remaining architectural decisions/blockers requiring approval: none within the retained scope. Integration target, migration purpose, transaction-fixture approach and datasource/profile conventions are resolved above. Implementation remains pending the user's explicit instruction to proceed; this is a work boundary, not an unresolved architecture decision. Creating/switching branches, commits, pushes or PRs still requires separate explicit authorization.
- Optional later work: concrete feature transaction/repository contracts, business conflict codes, API success/list/pagination conventions and schemas, auditing/locking models, BFF session integration and measured test-fixture optimization. Do not include them in #13 without a concrete consumer and approved scope.

## Planning checks performed

The initial planning pass read issue/comments through the connected GitHub plugin; root/skill instructions, conventions, workflow, testing and organization policies; Ecommerce source/tests/CI, Identity contract/ownership, adjacent persistence configuration, local infra, branch refs and relevant history. GitHub branch search returned `phase/1-core-services-foundation`. This refinement rechecked Identity/Media datasource and local-profile files and the Ecommerce README; it did not refresh GitHub state. Updated only `.temp/13-plan.md` at the user's explicit request; this path is tracked, not ignored. Application source remains untouched. No tests executed or branch, commit, issue, comment or PR mutations.
