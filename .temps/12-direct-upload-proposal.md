# Direct browser upload alongside backend upload — discussion proposal

Status: Investigation only. The user requests direct upload as the future frontend path while retaining the existing multipart backend API. Implementation details below remain proposals; no application or durable documentation changes are authorized by this artifact.

## Evidence and boundaries

- Current `MediaAssetController`, `MediaAssetService`, `ImageCanonicalizer` and `CloudinaryStorage` implement server-mediated canonical JPEG/PNG upload, authenticated storage, read-after-write verification and durable cleanup. Retain this endpoint and its observable behavior.
- `MediaStorage.StoredAsset` currently contains identities/type/format only. Direct-upload verification needs trusted dimensions, bytes, creation/version information and processing evidence as appropriate; browser declarations and upload responses are not authoritative.
- V1 requires non-null final image metadata, with output capped at 4 MiB. An intent exists before these values are known. Do not populate fabricated dimensions/bytes or edit an existing migration. Choose an additive intent table or a carefully constrained additive lifecycle migration after selecting the processing design.
- `web/backoffice/src/App.tsx` remains a skeleton. There is no integrated BFF session transport or actual business upload screen yet. ADR-0001 keeps OAuth tokens outside browser JavaScript; direct storage upload does not change that.
- The previous Issue #12 plan explicitly deferred direct upload (D1), while recording the old repository's intent/complete flow. The new request extends that scope rather than correcting a package refactor regression.
- Local branch `feat/12-implement-media-service-draft`: cached integration base `origin/phase/1-core-services-foundation`, target-only 0 / current-only 2 at inspection. No remote refresh or synchronization; preserve the existing mixed implementation changes. Whether to include this expansion in #12 or a separate CR is still open.

## Legacy reference, not a migration template

Inspected `XdpsX/ecommerce-be` files: `CloudinaryUploadAuthorizer`, `MediaDirectUploadService`, `MediaDirectUploadPersistence`; and `XdpsX/ecommerce-backoffice` files `category-media-upload.api.ts`, `use-category-image.ts`.

Useful lessons: persist intent before exposing signed instructions; verify through provider inspection; idempotent completion; short database transactions around state changes; retry the same intent when provider/network/completion outcomes are unclear. Browser upload omits backend credentials.

Do not inherit category purposes, GIF support, attachment states, ownership rules for all assets, public URLs, eager callbacks or a large category-specific workflow hook. These are not current Media requirements. Do not treat this targeted inspection as a full legacy security review.

References:
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryUploadAuthorizer.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/upload/MediaDirectUploadService.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/upload/MediaDirectUploadPersistence.java
- https://github.com/XdpsX/ecommerce-backoffice/blob/main/src/features/categories/api/category-media-upload.api.ts
- https://github.com/XdpsX/ecommerce-backoffice/blob/main/src/features/categories/hooks/use-category-image.ts

## Recommended interaction

1. Browser requests an upload intent through the future authenticated, CSRF-protected BFF. Media validates `media:upload`, chooses a random exact provider identity and persists the pending operation before returning signed fields. Client size/MIME declarations are early validation only.
2. Browser POSTs the file and exact returned fields to the approved HTTPS Cloudinary endpoint. No BFF cookie, OAuth token or API secret is sent to the provider. API key is public identification; API secret remains server-side.
3. Browser asks Media, through BFF, to complete the same intent. Media uses its own provider credentials to inspect the exact server-selected identity and enforce the approved image processing policy. Never accept an arbitrary caller-provided inspection URL.
4. Only verified canonical assets become READY. Completion is idempotent; expose a separate intent-status response so existing READY-only metadata behavior remains compatible.
5. Provider timeout, browser abort or lost complete response keeps the same intent recoverable. Status polling does not silently mean provider verification. Use bounded retries and server reconciliation; do not issue another provider POST or remove an asset solely because the previous response was lost.

Keep the existing multipart API. The frontend selects direct upload explicitly; do not silently fall back to multipart after ambiguous failure, which could duplicate assets.

## Decisions before implementation

### Image normalization — material blocker

Preferred direction to evaluate: signed provider-side incoming normalization with authenticated delivery and trusted completion inspection. Prove JPEG/PNG policy, orientation, metadata stripping, private originals/derivatives and output limits with an isolated-account spike before selecting this implementation. Provider metadata inspection alone does not prove equivalent byte canonicalization. Input-limit enforcement and normalized-output limits must be distinguished; reject excessive inputs rather than silently resize them if that changes existing policy.

Alternative: upload to a private staging identity, then let Media fetch a bounded, trusted provider source, use the existing canonicalizer and write a separate final identity. This preserves local validation more directly but adds backend bandwidth, latency, two object identities and cleanup states. It still satisfies browser-to-provider upload, but does not eliminate backend image I/O. Request explicit selection after the spike; do not silently weaken the existing guarantees or rely on frontend validation.

### Other proposed decisions

- Restrict completing/cancelling a pending intent to its initiating administrator, in addition to operation permission. This is new narrow workflow ownership, not a change to current shared READY-asset read/delete permissions. Requires approval.
- Add request idempotency for intent creation, scoped to principal and payload with an explicit retention/reuse contract. Completion idempotency is required regardless; creation idempotency is a recommended enhancement.
- Distinguish application intent expiry, provider authorization validity and completion/reconciliation grace. Cloudinary documents signatures as valid for one hour; a five-minute application TTL does not revoke the issued signature. A signature is not bound to file bytes and is not inherently one-use. Sign fixed identity/options with overwrite disabled, apply quotas/rate limits, and reconcile late/replayed uploads; deleting the object while authorization remains valid can enable its recreation.
- Cleanup must retain enough identity/authorization history to handle late provider writes; no premature tombstone purge or folder deletion. Account for unsigned URL-path resource type (Cloudinary excludes `resource_type` from signature): reject unexpected type on completion and explicitly assess prevention/reconciliation of uploads under other resource types. Do not claim signed fields alone enforce every provider boundary.
- Prefer a separate CR after the current package migration, and defer production frontend wiring until BFF authentication exists. A real upload UI needs a confirmed consumer; do not invent category/catalog screens or expose browser bearer tokens to bypass BFF work.

Official references checked:
- https://cloudinary.com/documentation/authentication_signatures
- https://cloudinary.com/documentation/image_upload_api_reference
- https://cloudinary.com/documentation/upload_parameters

## Ordered implementation slices after decisions

1. Isolated provider feasibility spike and security/processing decision. Record measured provider behavior, limits and unresolved account configuration; no production credentials in tests or logs.
2. Additive OpenAPI intent/create/complete/status/cancel contract and lifecycle persistence. Preserve old routes, JWT claims and Identity entitlements; select exact state model and migration only after slice 1. No attachment semantics.
3. Application workflow and Cloudinary signing/inspection adapter under `asset/application/{service,port,model,exception}` and `asset/adapter/{web,persistence,storage}`. Reuse cleanup scheduling with explicit direct-upload deadlines; provider I/O outside database locks, conditional transitions against stale workers.
4. Real-chain API authorization, durable reconciliation and concurrency verification. Preserve complete/delete/cleanup serialization and prevent stale completion resurrecting a cancelled/deleted asset.
5. Frontend upload workflow under `features/media` when a real screen and BFF transport are in scope: prepare/upload/verify/ready/error, separate provider transport without backend credentials, recover same intent, discard stale results after file/session changes. Do not copy category-specific state or add a universal media library.
6. Document both upload paths, processing guarantees, expiry/replay limitations, BFF dependency, configuration and operator recovery. Keep old multipart regression coverage and update generated API consumers when present.

## Minimal meaningful verification

- Existing multipart image/lifecycle/security suites remain green.
- Provider HTTP fixture checks exact signed fields, authenticated delivery, overwrite protection and sanitized failures; completion rejects forged metadata and mismatched identity/type/format/size/dimensions.
- PostgreSQL tests: duplicate/concurrent completion; complete versus cancel/cleanup; expired lease reclaim; upload after cancel/expiry; provider succeeds but response is lost; transient inspect failure; no READY before processing evidence.
- Real security-chain tests: issuer/audience/scope/permission, intent ownership, no changes to READY shared access. Contract tests for new operations and existing response compatibility.
- Frontend tests: no credentials to provider, same-intent retry, no duplicate upload after ambiguous outcome, stale file/session responses ignored, expired authorization recovery. BFF session/CSRF integration and browser CORS E2E when BFF is available.
- Isolated Cloudinary smoke: private original/derived denial, canonicalization/EXIF results, signature lifetime/replay behavior and late-upload reconciliation. Fixtures alone cannot prove these.

No implementation or runtime tests were run during this planning investigation. Only this ignored plan artifact was added.
