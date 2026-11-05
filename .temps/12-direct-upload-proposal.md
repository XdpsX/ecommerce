# Direct browser upload alongside backend upload — V1 proposal/spec

Status: Design proposal updated after discussion; **not implementation authorization**. This file records agreed product/API direction, recommendations, and unresolved provider feasibility gates. Preserve the existing server-mediated multipart upload and its security guarantees. Do not silently weaken D2/D4 or implement before the feasibility decision.

## Scope and existing evidence

- Current `MediaAssetController`, `MediaAssetService`, `ImageCanonicalizer` and `CloudinaryStorage` provide server-mediated canonical JPEG/PNG upload, authenticated storage, read-after-write verification and durable cleanup. Keep existing endpoint and behavior unchanged.
- D2 remains the baseline: JPEG/PNG only; input <= 5 MiB, <= 20 MP and <= 10,000 px per side; canonical decode/re-encode, EXIF orientation and metadata removal; final output <= 4 MiB. Direct upload must prove equivalent enforcement or request explicit policy change.
- D4 remains the baseline: authenticated/private originals and derivatives; authorized short-lived download access (~60 seconds proposed). Signed delivery URLs are not automatically expiring URLs.
- `MediaStorage.StoredAsset` currently exposes identity/type/format but not all trusted final metadata. Browser declarations and browser-supplied upload responses are never authoritative.
- V1 `media_assets` final metadata is non-null; intent is created before those fields exist. Prefer a separate additive `media_upload_intents` table; finalize/create an asset only with verified metadata. Do not rewrite existing migrations or invent placeholder dimensions/bytes.
- `web/backoffice/src/App.tsx` is a skeleton; no complete BFF/session transport or Product upload screen exists yet. ADR-0001 keeps OAuth tokens out of browser JavaScript. Do not bypass BFF using browser bearer tokens.
- Previous Issue #12 D1 deferred browser-direct upload. This is an explicit scope extension; a separate CR/issue after current Media work is preferred, but scheduling remains open. Do not modify implementation as part of this proposal.

## Agreed V1 product and API direction

1. **Private by default**: Media stores assets as authenticated; Product controls ACTIVE/INACTIVE visibility and Product-to-Media references. Media does not query Product state or automatically toggle Cloudinary visibility when a product is published. Storefront delivery for published products is a separate integration decision; private storage alone cannot revoke already issued links, cached images or downloads.
2. **Keep multipart backend upload** as an independent supported workflow; no automatic fallback from an ambiguous direct-upload attempt.
3. **One intent per file**, with a **single batch-create endpoint accepting 1..N files**. No separate public single-create endpoint. Batch intent creation is all-or-nothing: validate the entire declaration set before persisting the batch. Each intent has its own provider identity, uploader, expiry and status.
4. **One batch-complete endpoint accepting 1..N intent IDs**, with **per-item partial results**. No separate public single-complete endpoint. Each item is inspected/finalized independently; one invalid or temporarily unavailable upload must not roll back successful items. Completion is idempotent and safe under duplicate/concurrent calls.
5. **One batch-status/read endpoint** to recover from lost upload/complete responses and inspect individual intent states; no separate single-status endpoint required.
6. Frontend sends one batch-create request, uploads each file directly to Cloudinary (bounded concurrency), then sends one batch-complete request for completed provider uploads; may complete smaller subsets when necessary. Ten images typically require two Media write requests plus ten Cloudinary uploads, excluding retries/status. Do not wait indefinitely for one failed upload before completing successful ones.
7. Media never owns gallery ordering, primary-image selection or Product association. Product stores READY Media UUIDs and owns gallery semantics.
8. **No public cancel endpoint in V1** unless a concrete product requirement emerges. Unused/abandoned intents expire and are reconciled/cleaned up. Expiry does not immediately revoke an issued Cloudinary signature.
9. **Batch-create idempotency key is deferred from minimum V1** to reduce scope; duplicate intent batches after lost create responses are possible and must be bounded by quotas, expiry and cleanup. Reconsider if observed duplicates/cost warrant it. **Batch-complete idempotency is mandatory**. Do not build a generic cross-service idempotency framework.

### Illustrative HTTP contract — exact routes/schema not finalized

```http
POST /api/admin/media/upload-intents
Content-Type: application/json

{
  "files": [
    {"clientFileId":"front","filename":"front.jpg","contentType":"image/jpeg","sizeBytes":2500000},
    {"clientFileId":"back","filename":"back.png","contentType":"image/png","sizeBytes":1800000}
  ]
}
```

Response: one entry per `clientFileId`, with opaque `intentId`, future `mediaId` if allocated, HTTPS provider upload URL, exact server-signed form fields and authorization expiry. `clientFileId` is only a response correlation token; it is not a trusted provider identity. Validate duplicates, batch count, per-file declarations and total requested size; choose concrete N and quota values before coding.

```http
POST /api/admin/media/upload-intents/complete
Content-Type: application/json

{"intentIds":["intent-1","intent-2"]}
```

Response: ordered per-intent results such as `READY(mediaId)`, `PENDING/RETRYABLE`, or `REJECTED(errorCode)`; define exact enum/error contract in OpenAPI. A valid batch request may return mixed results; malformed request, duplicate IDs, excessive batch size or missing batch-level authorization fail as request errors. Decide whether a per-item unauthorized/not-found result is indistinguishable to avoid information leaks. Repeat completion of READY returns the same media identity. Provider I/O is never performed inside a long-lived DB transaction.

```http
POST /api/admin/media/upload-intents/status
Content-Type: application/json

{"intentIds":["intent-1","intent-2"]}
```

This is a proposed batch status shape, not a locked REST contract. Status reads database state and do not implicitly claim provider verification. Define paging/maximum batch size and ownership checks.

### Example: ten Product images

- Batch create persists ten independent pending intents atomically and returns ten signed instructions.
- Browser uploads ten files to Cloudinary with bounded parallelism (e.g., 3 concurrent, a tuning example not a requirement).
- Browser batch-completes the uploads that reached Cloudinary. If eight verify, one is not yet visible and one is invalid, return eight READY plus two distinct item outcomes. Retry only unresolved intents; Product attaches only READY media IDs.
- Reconciliation covers cases where Cloudinary succeeded but browser never called complete.

## Security and provider flow

1. Browser calls Media via authenticated, CSRF-protected BFF. Media checks `media.api` scope/audience and `media:upload` permission, validates declared file properties, selects unguessable exact provider identities, persists all batch intents **before releasing any signed instructions**, then signs server-controlled upload parameters. Avoid returning partial usable signatures if persistence fails.
2. Browser uploads bytes and exact signed fields to approved Cloudinary HTTPS endpoint. Never send BFF cookies, OAuth access tokens or Cloudinary API secret to Cloudinary. API key is public identification, not secret.
3. Browser batch-completes via BFF. Media inspects the **server-stored** Cloudinary identity using its own credentials, verifies resource type, delivery type, provider asset identity, timestamps, dimensions, bytes, format, processing evidence and policy. Do not trust caller-provided Cloudinary URLs or metadata.
4. Restrict completing/status-reading pending intents to the initiating admin plus permission, as the proposed narrow workflow ownership rule. Existing shared READY read/delete authorization remains unchanged; final ownership policy still requires explicit approval.
5. Each successful intent transitions to READY exactly once with trusted final metadata; retries return stable results. Conditional transitions/row locks serialize completion with expiry and cleanup; do not resurrect deleted/expired assets.
6. Keep separate **intent TTL**, **provider signature validity** and **completion/reconciliation grace**. Cloudinary upload signatures are documented as valid for approximately one hour; they are not one-time, not bound to the bytes, and application expiry does not revoke them. Use exact server-chosen identity and `overwrite=false`, bounded quotas, retained cleanup identity/tombstone through replay horizon, and reconcile late writes. Deleting while a signature is still usable can allow re-creation.
7. Cloudinary upload URL path's `resource_type` is not covered by the signature in the same way as signed parameters; test/review cross-resource-type behavior. Inspect and reject mismatches; do not assume inspection of `image` alone discovers every malicious alternate-type upload.
8. No implicit direct-to-multipart fallback on timeout. Recover the same intent first; avoid duplicate provider uploads after ambiguous responses.

## Feasibility gates — must resolve before choosing provider implementation

**Preferred candidate A1:** signed browser upload with Cloudinary **incoming transformation** and `authenticated` delivery, followed by server-side inspection. Incoming transformation differs from eager transformation: eager derivatives do not prove the original was canonicalized. Documentation establishes feature availability, **not** equivalence to the current Java canonicalizer.

**Fallback A2:** private staging upload, Media downloads bounded source, runs existing canonicalizer, writes separate authenticated final asset and cleans staging. More backend bandwidth/latency, extra identities and cleanup; preserves D2 more directly. Select A2 only if A1 cannot prove D2/D4, or explicitly approve a changed policy.

Isolated Cloudinary-account spike, with documented configuration and observed outcomes:

| Gate | Required verification |
| --- | --- |
| Incoming normalization | EXIF GPS removed; orientation applied; JPEG/PNG re-encoded; final bytes/format and metadata policy equivalent to D2 |
| Input constraints | Reject >5 MiB, >20 MP or >10k px/side **before** an automatic resize could mask invalid input; reject disguised GIF/other formats |
| Output constraints | Final output <=4 MiB and trusted final dimensions/bytes; inspect whether processing is synchronous and what evidence is available |
| Private delivery | Anonymous original and transformed URLs denied; `authenticated` rather than merely `private` Cloudinary delivery |
| Expiring access | Prove real ~60-second expiry for authorized delivery, including account/plan restrictions; a normal signed URL is not inherently time-limited |
| Signature and replay | Expiry behavior, repeated POST, `overwrite=false`, late upload after application expiry, delete/recreate possibility |
| Identity/inspection | Provider Admin API can verify correct resource/delivery type, identity, timestamps, context and processing evidence |
| Alternate resource types | Signed upload against unexpected `raw`/`video` URL path and whether orphaned objects can be discovered/removed |
| Browser integration | CORS and form upload from actual Backoffice origin when BFF and UI exist |

Cloudinary official references for follow-up verification:
- https://cloudinary.com/documentation/authentication_signatures
- https://cloudinary.com/documentation/image_upload_api_reference
- https://cloudinary.com/documentation/upload_parameters
- https://cloudinary.com/documentation/control_access_to_media

No live Cloudinary smoke has been executed. Do not mark any gate passed from documentation alone.

## Minimal persistence and reconciliation

Prefer a separate additive `media_upload_intents` table because final `media_assets` metadata is required. Store identity, initiating principal, deadlines, status, provider identity, necessary signature/processing context, retry bookkeeping and stable completion result. Do not store secrets or fake final metadata. Use only as many states as needed: pending, ready/completed, expired/rejected (and internal cleanup tracking as needed); avoid copying legacy attachment/derived-processing state machines.

A **single bounded scheduled reconciliation worker** is sufficient initially, integrated with existing durable cleanup:
- Find due pending/uncertain intents and inspect exact Cloudinary identity outside DB transactions.
- Valid asset and still eligible: finalize with conditional state transition.
- Not yet found: retry within grace; after safe authorization/replay horizon, expire and clean up.
- Transient provider errors: bounded backoff; invalid or late asset: reject/cleanup without making READY.
- Keep identity/tombstone long enough to discover late writes. Coordinate concurrent complete, reconcile and cleanup; never delete a verified READY asset due to stale work.

Do **not** build a generic reconciliation framework, distributed orchestration engine, multi-queue processing system or generic media library for V1. Reconciliation repairs DB/provider divergence; it does not itself canonicalize images.

## Implementation slices after feasibility decision

1. Small isolated Cloudinary spike and written pass/fail decision A1 vs A2; no production credentials in tests/logs.
2. Additive OpenAPI contracts for batch create (1..N, atomic), batch complete (1..N, per-item results), batch status, and intent persistence. Choose concrete limits/error enums/ownership. Preserve current JWT claims, Identity entitlements and existing Media routes.
3. Implement application `createBatch`, `completeOne` (internal reusable operation), `completeBatch`, `getStatuses`; Cloudinary signing/inspection adapter and minimal intent persistence. Short DB transactions; no provider I/O under locks.
4. Implement bounded reconciliation and cleanup, authorization and concurrency protections; retain original multipart behavior.
5. Integrate Backoffice when real Product upload screen and BFF session/CSRF transport exist. Browser never holds OAuth token or provider secret. No category-specific workflow or universal media library.
6. Document provider configuration, D2/D4 guarantees, expiry/replay limitations, operations and both upload paths.

## Minimum verification

- Existing multipart image/security/lifecycle tests unchanged and green.
- Batch create: 1 and N items, invalid item rejects whole batch, duplicate client IDs, batch size/quota, authorization, atomic persistence before signing.
- Batch complete: mixed READY/retryable/rejected outcomes, repeated completion stable, duplicate/concurrent calls, no DB transaction held over provider I/O, per-item ownership and information-leak protection.
- Batch status: pending/ready/error results, cross-admin access checks, missing IDs and bounded list.
- Provider fixtures: signed field constraints, authenticated delivery, `overwrite=false`, forged metadata and identity/type/format/size/dimension mismatches.
- PostgreSQL/concurrency: complete vs reconcile/expiry/delete, stale worker cannot resurrect; provider success with lost response; retryable inspect failure; late/replayed uploads and cleanup.
- Browser/BFF when available: no OAuth token/cookie/API secret sent to Cloudinary, bounded parallelism, partial batch recovery, stale file/session result protection, CORS.
- Real Cloudinary smoke must pass the feasibility gates; mocks cannot prove normalization or private delivery.

## Legacy reference — reuse principles, not behavior

Inspected `XdpsX/ecommerce-be`: `CloudinaryUploadAuthorizer`, `MediaUploadIntentService`, `MediaDirectUploadService`, `MediaDirectUploadPersistence`, `MediaUploadedAssetValidator`, `CloudinaryMediaAssetInspector`, `MediaReconciliationService`; and legacy Backoffice category upload hooks.

Reuse: persist intent before exposing signed fields, inspect provider identity, idempotent completion, short DB transactions and recovery. Do **not** inherit GIF support, public `type=upload` delivery, category purposes/attachments, eager callbacks, shared ownership rules or oversized processing framework. Legacy inspection does not prove canonicalization or D4 authenticated delivery.

References:
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryUploadAuthorizer.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/upload/MediaDirectUploadService.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/processing/MediaReconciliationService.java

## Explicitly deferred / open decisions

- Deferred V1: public cancel, separate single-item endpoints, generic create-idempotency framework, generic Media Library, batch-level rollback after provider upload, Product attachment semantics inside Media, broad frontend abstractions.
- Open: exact batch maximum and aggregate quota, exact OpenAPI status/error shapes, initiating-admin ownership approval, A1/A2 choice after spike, expiring authenticated delivery mechanism/account tier, Storefront image delivery for published Products, and whether this expansion becomes a separate CR/issue.
- **No application code, migrations, Cloudinary runtime tests or frontend wiring are authorized by this document update.**
