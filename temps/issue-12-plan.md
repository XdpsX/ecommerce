# Issue #12 — Media Service implementation plan

Status: proposed; material decisions below require approval before implementation.
Inspected: 2026-11-04. Planning only; no application changes or tests executed.
Issue: https://github.com/XdpsX/ecommerce/issues/12

## Recommended scope

Deliver a standalone Spring MVC Media resource server with image upload, metadata retrieval, controlled preview access and asynchronous deletion/recovery. Media owns its PostgreSQL metadata and provider objects. Extend Identity only to issue Media audiences/permissions and safely update an explicitly selected existing client. Do not implement BFF, frontend, product galleries, attachment APIs, event infrastructure or legacy data migration.

Recommend Cloudinary behind an application-owned storage port, server-mediated multipart upload, authenticated provider assets and short-lived original-image download links. This is a proposal, not an approved provider requirement. Keep READY assets until explicit deletion: automatic unused-image TTL requires an attachment/reference contract that this issue excludes. Failed/incomplete uploads and requested deletions still receive durable cleanup.

## Branch and dependency evidence

- Current branch: `feat/11-implement-identity-service`, HEAD `e9ae99f4f5df705771fbbdf9edeea2dd9018e824`; clean working tree, matches its remote branch.
- Actual phase branch: `phase/1-core-services-foundation`, SHA `8798301614c3afa2e79148ecf16f8ca7b8736414`. Issue says `phase/1`, which does not exist in the remote branch list. Recommend the actual phase branch; confirm before implementation.
- Local remote-tracking refs match GitHub branch API observations. Target/current divergence: target-only 0, current-only 5 commits. No fetch, merge, rebase or branch switch performed.
- PR #16 is OPEN and not merged at inspection. Identity behavior below exists on the current branch, not yet on the target. Prefer waiting for #11 integration, then start an explicitly authorized #12 branch from the phase branch. Stacked work requires a separately agreed base/PR arrangement; do not implement #12 on the #11 branch by default.

## Current behavior and sources

| Evidence | Consequence |
| --- | --- |
| `services/media/pom.xml`: only basic Boot starter/test; Java 25, Boot 4.1.2 | No HTTP, security, persistence, Flyway, storage SDK or IT runner yet. Select compatible dependencies during implementation; retain the baseline. |
| `services/media/src/main/java/com/ecommerce/media/MediaApplication.java`, `application.yaml`, `MediaApplicationTests.java` | Entry point, service name and context-load test only. No current API/schema/lifecycle to preserve. No Media README exists; create one. |
| `infra/compose/compose.yml`, `init.sql`, `infra/README.md` | PostgreSQL/Redis only; init currently creates Identity DB. Add Media DB provisioning and existing-volume instructions, never reset volumes. |
| `services/identity/.../token/AccessTokenCustomizer.java` | Only `ecommerce.api` and `identity.admin` map to audiences. Permissions are filtered by requested audience. Media cannot use current tokens without an Identity change. |
| `services/identity/.../client/ClientRegistrationService.java` | Scope allowlist excludes Media; client registration rejects changed existing scopes. Updating defaults alone will neither upgrade a persisted client nor allow re-registration. |
| `services/identity/.../configuration/IdentityProperties.java`, `application.yaml`, migrations V1–V5 | New Media scope configuration and an additive migration are needed; never edit applied migrations. SUPER_ADMIN has only Identity management permissions today. |
| `services/ecommerce/.../security/ResourceServerSecurityConfiguration.java` and security ITs | Proven independent JWT-validation pattern; reference rather than introduce a shared Java security library. |
| `contracts/openapi/common/problem.yaml`, `docs/04-contracts/http-errors.md` | Reuse Problem Details schema; keep detail a string, validation in errors[], safe advice/filter handlers. |
| `.github/workflows/media-ci.yml` | Runs Maven verify but only uploads Surefire reports; no contract/Identity cross-service path triggers. Add Failsafe and necessary triggers/build ordering. |

Architectural constraints: ADR-0001 keeps browser sessions/token relay in the future BFF; Media independently validates bearer JWTs. ADR-0002 keeps roles/permissions in Identity; no role-only checks or remote Identity table lookups. Follow `docs/06-development/code-organization.md` and `testing.md`.

Legacy reference inspected at commit `bd6565fddf4f09ea5015a24a23f5e997503d077f`:

- [Usage](https://github.com/XdpsX/ecommerce-be/blob/bd6565fddf4f09ea5015a24a23f5e997503d077f/docs/media/usage.md): Cloudinary direct upload/complete, separate upload/processing/attachment states, uploader ownership.
- [Operations](https://github.com/XdpsX/ecommerce-be/blob/bd6565fddf4f09ea5015a24a23f5e997503d077f/docs/media/operations.md): provider timeouts, late uploads, deletion retries, retained tombstones and quota handling.
- `MediaServiceImpl`, `MediaCleanupService`, `Media`: multipart compensation and claim-based cleanup, but monolith DB/FK/ownership coupling is not suitable for copying into a separate service.

Adopt the failure scenarios as test inputs. Do not copy ADMIN-only checks, cross-module persistence access, purpose-specific dimensions, direct-upload/webhook machinery or entity attachment rules.

## Decisions requiring approval

| ID | Recommendation | Alternative / implication |
| --- | --- | --- |
| D1 Storage | Cloudinary, one adapter; server-mediated multipart upload. Use an isolated environment/prefix, external secrets, fixed image resource type and authenticated delivery type. | S3-compatible private object storage is viable, but changes local fixtures, SDK, delivery and operational setup. Direct browser upload adds signed-intent/completion/replay complexity; defer. |
| D2 Supported files | JPEG/PNG only, maximum 5 MiB input, 20 megapixels and 10,000 pixels per side; one file per request. Decode/re-encode a canonical image and remove metadata; bound output too. | These are proposed product limits, not inherited rules. SVG/GIF/WebP/video/PDF, malware scanning and transformations are deferred; supporting them needs another validation policy. |
| D3 Access | `media:read`, `media:upload`, `media:delete`, audience `media-api`, scope `media.api`. Initially grant to SUPER_ADMIN. Permissions apply to all Media in this single-shop admin system; uploader UUID is audit data, not an ownership restriction. | Uploader-only access would be an additional contextual policy needing explicit approval. No tenant/owner framework. |
| D4 Delivery | Authenticated provider assets. Authorized link endpoint returns an expiring original-image URL, proposed TTL 60 seconds. URL is a bearer capability until expiry; never persist/log it. | Public catalog CDN delivery is a distinct approved contract, not implied by secure metadata. Proxy delivery avoids client-visible provider links but adds bandwidth/streaming responsibility. |
| D5 Lifecycle | READY remains until explicit authorized deletion; no automatic TTL for healthy assets. Durable cleanup for interrupted uploads and deletion requests. Keep tombstones for ambiguous uploads; do not promise zero orphans. | A 24-hour temporary TTL is safe only when temporary/permanent promotion and reference protection are defined. Adding that now expands the attachment scope. |
| D6 Identity client update | Keep registration's mismatch rejection. Add a narrowly scoped operator command to explicitly add `media.api` to a named existing confidential client, preserving credentials, redirects and other settings; audit and make idempotent. Fresh registration accepts Media scope normally. | A separately registered test/client can avoid migration but does not upgrade the existing future BFF registration. Never silently mutate it at startup. |
| D7 Integration/base | Use `phase/1-core-services-foundation` after #11 integrates. Ecommerce integration in #12 is a documented media-ID/ownership contract only; no speculative runtime calls. | If a real Ecommerce consumer or attach/publish flow is required now, identify its use case and re-plan its consistency/deletion rules first. |

Provider guidance: [Cloudinary access controls](https://cloudinary.com/documentation/control_access_to_media) distinguish private originals from authenticated assets; use authenticated to protect derived assets too. `private_download_url` supports authenticated type and explicit expiry; normal signed delivery URLs must not be assumed to expire. [Upload API](https://cloudinary.com/documentation/image_upload_api_reference) documents upload/delete operations. [OWASP upload guidance](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) supports allowlists, size limits, content validation, generated storage names and safe serving. Verify chosen SDK behavior with a real isolated account before claiming production readiness.

## Proposed contracts and ownership

| Endpoint | Permission | Behavior |
| --- | --- | --- |
| `POST /api/admin/media` | media:upload | Multipart `file`; validate/sanitize, persist operation, upload, finalize. 201 + Location only for READY media. No arbitrary remote URL ingestion. |
| `GET /api/admin/media/{id}` | media:read | Stable UUID, status, verified media type/bytes/dimensions, uploader and timestamps. No provider credentials/internal identifiers or permanent delivery URL. 404 for unknown/deleted IDs. |
| `POST /api/admin/media/{id}/download-link` | media:read | READY only; URL + expiresAt, no-store. Unknown/deleted 404; incomplete/deleting 409. No arbitrary transformations. |
| `DELETE /api/admin/media/{id}` | media:delete | 202 after durable delete intent; repeated known deletion remains idempotent, unknown ID 404. Provider unavailable does not undo accepted delete intent. |

Proposed upload errors: 400 malformed/invalid multipart; 413 FILE_TOO_LARGE; 415 UNSUPPORTED_MEDIA_TYPE; 422 INVALID_MEDIA_IMAGE; 409 MEDIA_STATE_CONFLICT; 503 MEDIA_PROVIDER_UNAVAILABLE with safe retry guidance. Align codes and examples in `contracts/openapi/media.yaml`; reuse common Problem Details and Bearer challenge semantics. Failed synchronous uploads return an error, never success for an unverified asset. A lost response followed by a new POST can create another asset; upload idempotency keys are outside this initial contract and must be documented.

Media owns metadata, uploader audit, generated provider identity, cleanup attempts and lifecycle. Identity owns entitlements. Ecommerce will own catalog/product relationships and persist stable media UUIDs, never provider IDs. No cross-service FK, shared DB or direct Java domain imports. Future consumers must establish attachment/deletion protection before storing durable references; current deletion is intentionally not safe for such an unimplemented consumer. Document this restriction explicitly.

## Lifecycle and failure model

- Persist an UPLOADING row with unique server-generated object identity before any provider write; commit this short transaction first. No client-chosen storage path or overwrite. Store canonical validated attributes and operation timestamps.
- Perform provider I/O outside DB transactions. On verified success, atomically transition UPLOADING to READY. On uncertain result or finalization failure, preserve the row for reconciliation/cleanup. Never erase recovery evidence on rollback.
- A delete request transitions READY to DELETE_PENDING using a conditional write; while an upload is active return 409. Recovery of abandoned UPLOADING rows claims a lease after a conservative timeout beyond configured I/O duration. Lease/version checks prevent stale workers from marking READY after cleanup starts.
- Cleanup claims bounded batches with short DB transactions and expiring leases, performs provider I/O outside locks, then conditionally records the result. Handle multiple instances and process crashes. A definite provider not-found is idempotent for ordinary deletion; timeout/auth/rate-limit errors are not proof of absence.
- Deleting an ambiguously completed upload is not enough to prove an in-flight provider upload cannot appear later. Retain its tombstone and reconcile the exact generated identity periodically; alert on persistent ambiguity. Do not retry upload automatically, reuse object IDs, mass-delete by prefix or automatically purge these tombstones.
- Proposed operational defaults: provider request timeout 30 seconds, abandon threshold 10 minutes, cleanup batch 10 each minute, exponential retries with jitter capped at 1 hour, honor provider retry/rate-limit responses; continued failures remain visible and retryable. Validate SDK timeout coverage and worker overlap during implementation; do not infer remote cancellation from local timeout.
- Record non-sensitive status/attempt/error-category/next-attempt information; provider diagnostics, credentials, images and signed URLs must stay out of client responses and logs. After accepted deletion, deny new delivery links; existing links/copies can survive until expiry/provider behavior and cannot be recalled from clients.

## Ordered implementation phases

Each phase is a small review boundary suitable for `$cr-implement`; commits still require explicit authorization. Use focused tests at each step and full verification at handoff. No automated delegation is assumed.

### P0 — Confirm scope and base

Resolve D1–D7, confirm acceptance examples and observe #11 integration. Recheck branch/working tree; obtain branch authorization separately. If storage/access changes, update this local plan before coding. Durable design documentation can record approved decisions; do not create an ADR without approval.

### P1 — Media persistence and service foundation

- Add MVC, validation, resource-server, JDBC, PostgreSQL/Flyway and Failsafe/Testcontainers dependencies compatible with the existing baseline.
- Add typed settings, isolated `media` DB, initial migration and indexes/check constraints for operation states/leases/object identity. Secrets external; local Compose may use the established local role, production credentials are scoped to Media DB.
- Add fresh/existing-volume provisioning in `infra/compose/init.sql`, `.env.example`, `infra/README.md`; never edit `.env` or reset volumes. Configure explicit Media port, proposed 9001, local `.env` loading convention and fail-fast settings.
- Package under `com.ecommerce.media`: service composition `configuration`; `asset/domain`, `asset/application` (queries/use cases/ports/exception), `asset/api/http`, `asset/infrastructure/persistence`, `asset/infrastructure/storage`, `asset/infrastructure/scheduling`; `security` and `api/error` technical adapters. Create only packages that acquire a responsibility; no giant generic service package.
- Verify real PostgreSQL migrations/restart and state constraints. Adapt the skeleton context test to its actual dependencies rather than bypassing production configuration.

### P2 — Identity Media contract

- Add the next additive Identity migration for the three Media permissions and SUPER_ADMIN mapping. Preserve last-admin invariants and all existing permissions.
- Extend `AccessTokenCustomizer`, client scope allowlist and client configuration to support media.api -> media-api, retaining audience-filtered permissions and existing audiences.
- Implement the explicit scope-update command from D6 through an application-owned port: non-web, explicit client selection, fixed scope addition, audit, atomic/idempotent update; no secret/password arguments or replacement of client settings. Keep existing register command's behavior.
- Test migration on existing Identity DB, scope update absent/existing/already-updated clients, no unrelated changes; initial and refreshed Media tokens; Media permissions absent when Media scope omitted; old Ecommerce/Identity flows unchanged. CLI smoke checks non-web exit behavior.

### P3 — Validation and Cloudinary adapter

- Implement application-owned storage capability (upload, inspect, delete, expiring original URL), concrete adapter and generated identity isolation. Pin a maintained compatible SDK version after checking official SDK/release docs; no arbitrary version chosen by this plan.
- Validate byte limit during bounded read, magic/decoder format, dimensions before full decode, decoded pixel budget and bounded sanitized output. Re-encode JPEG/PNG, strip metadata, close streams/readers, reject corrupt/truncated/polyglot input that cannot be safely canonicalized. Ignore claimed MIME as proof, never use original filename as a path.
- Use authenticated assets, no overwrite/eager transformations/webhook, bounded timeouts and sanitized exception categories. Hard-code neither secrets nor live account configuration. Reject provider result identity/type mismatch.
- Unit tests for meaningful validation boundaries. Test actual HTTP/SDK serialization against a controlled provider fixture for upload/inspect/delete/link generation; mark these as adapter tests, not proof of Cloudinary behavior. An isolated real-provider smoke is a separate explicit verification gate.

### P4 — Upload, metadata and secure delivery vertical slice

- Implement persisted UPLOADING -> READY orchestration, failure recording, application queries and HTTP DTOs. Use injected Clock and conditional state updates; transport models do not enter application/domain.
- Add real stateless JWT chain: RS256, configured issuer/JWKS and media-api audience, exp/iat/sub and claim-shape validation, max 30-second clock skew. Require media.api and operation permission; roles alone grant nothing. Deny unknown routes. No session login, browser token handling, permissive CORS or production test probes.
- Add centralized advice and security error writers conforming to the shared error schema. Handle multipart/parser/size errors and no-store link/error responses without exposing raw values.
- Add `contracts/openapi/media.yaml` for implemented endpoints; schemas/examples reflect verified metadata, explicit states, URL expiry, asynchronous deletion semantics and all relevant errors.
- IT: real PostgreSQL + provider fixture + HTTP/JWT chain; upload -> metadata -> authorized link; missing/invalid/wrong-audience token 401, missing permission/scope 403; role-only denial; invalid upload cannot become READY; provider failure and DB-finalization failure preserve recovery evidence. Test JWT expiry and claim validation without fictitious production endpoints.

### P5 — Deletion and crash recovery

- Implement deletion endpoint, persistent job claims/leases and abandoned-upload recovery described above. Keep recovery scheduling configurable, with enabled state and pause instructions documented; once configured for runtime, cleanup must not silently remain disabled.
- Tests: repeated delete, unknown ID, provider absent, provider timeout/429, DB failure after provider delete, abandoned upload, late provider success, stale worker, two workers claiming the same asset, process-crash lease expiry and retry persistence across restart. Assert READY assets are never age-deleted.
- Deny metadata/link reads after deletion begins as documented; no pretend cross-service attachment safety. Expose actionable logs/counters or an operator query for backlog and oldest pending age without introducing a monitoring platform.

### P6 — Cross-service verification, docs and CI

- Add one real Identity Authorization Code + PKCE -> Media upload/read/denial flow using packaged Identity, generated keys, real PostgreSQL and controlled storage fixture. Reuse the proven Ecommerce IT approach without adding shared runtime libraries or BFF.
- Update Media CI with Failsafe reports, Identity package prerequisite and relevant Identity/common-contract/Media contract path triggers. Ensure this flow actually runs under Maven verify and CI; affected Identity/Ecommerce checks remain applicable.
- Add `services/media/README.md`; update contract index, approved Media architecture/data ownership guidance, infra/env/bootstrap commands and affected architecture status references. Explain limits: JWT snapshot, signed-link capability, retained images, cleanup ambiguity, no attachment safety/public publishing, real-provider smoke status.
- Real Cloudinary smoke in an isolated account: authenticated upload; public original/derived URL denial; authorized expiring original link works then expires; exact-object delete/retry and not-found handling. Report credentials/account prerequisites as unverified when unavailable; do not substitute a mocked result or put live secrets in Git/CI logs.
- Acceptance handoff maps each Issue #12 criterion to tests and documents remaining consumer attachment work as deferred.

## Final verification commands and sequencing

After implementation, from repository root (PowerShell examples):

```powershell
.\services\identity\mvnw.cmd -f services\identity\pom.xml -B --no-transfer-progress verify
.\services\media\mvnw.cmd -f services\media\pom.xml -B --no-transfer-progress verify
.\services\ecommerce\mvnw.cmd -f services\ecommerce\pom.xml -B --no-transfer-progress verify
git diff --check
docker compose --env-file .env -f infra/compose/compose.yml config --quiet
```

Docker is required for ITs; Identity verify packages the JAR consumed by dependent suites. Media verification must not require production keys or Cloudinary credentials. Add a reproducible OpenAPI validation command/tool as part of contract integration; select from existing workspace tooling after inspection, do not rely on ignored helper scripts. Resolve external common-schema refs and validate representative success/error examples. Verify changed relative Markdown links and actual CI runs when a PR exists. Browser/BFF E2E is deferred; no frontend changes planned.

Deploy Identity migration/code first, explicitly update the chosen client scope, provision Media DB/storage/secrets, deploy Media migrations/app and enable/observe cleanup, then enable callers requesting media.api. Existing JWTs/refresh authorizations do not automatically acquire an unrequested new scope: obtain a fresh authorization grant. Roll back consumers before Media; preserve DB/object metadata and recovery workers/tombstones. Retain additive Identity catalog entries if rolling back application code; do not delete provider data as rollback.

## Acceptance and residual risks

- Secure upload/metadata: P2/P4 plus real Identity-to-Media IT; direct service access enforces JWT and permissions.
- Storage/lifecycle: P1/P3/P5 plus adapter fixtures and isolated Cloudinary smoke; durable cleanup, bounded retries and ambiguous-outcome handling.
- Validation/auth/cleanup tests: P3–P6, explicitly discovered by Surefire/Failsafe and CI.
- Cloudinary introduces external availability/quota and real-account verification needs. A fixture validates our protocol behavior but cannot establish provider guarantees.
- Server upload/re-encoding consumes CPU/memory/temp disk: configured size/pixel/concurrency budgets matter; no claim of malware scanning or immunity to decoder vulnerabilities.
- No automatic unused-image TTL means READY assets accumulate until explicit deletion. No safe product attachment/publish flow is delivered by this issue; subsequent consumers must approve and implement one before durable use.
- No application tests, provider smoke, migrations, Compose changes or remote CI were executed during this planning task. Read-only source/history/API inspection only; the sole new file is this ignored plan artifact.
