# Direct browser upload alongside backend upload — V1 proposal/spec

Status: **A1 continuation approved with signed Cloudinary eval for the per-intent input byte limit; production implementation is not authorized.** This proposal incorporates the isolated-account A1 and signed-eval spike evidence. Remaining D2/D4 gates and the full proposal must be settled before production code. Preserve the existing backend multipart endpoint and its security guarantees; do not weaken D2/D4, choose A2 or add a temporary input-limit exception.

## Scope and existing evidence

- Current `MediaAssetController`, `MediaAssetService`, `ImageCanonicalizer` and `CloudinaryStorage` provide server-mediated canonical JPEG/PNG upload, authenticated storage, read-after-write verification and durable cleanup. Keep existing endpoint and behavior unchanged.
- D2 remains the baseline: JPEG/PNG only; input <= 5 MiB, <= 20 MP and <= 10,000 px per side; canonical decode/re-encode, EXIF orientation and metadata removal; final output <= 4 MiB. Direct upload must prove equivalent enforcement or request explicit policy change.
- Input byte enforcement for A1 is now approved to use backend-generated **signed eval against original `resource_info.bytes`**, bounded by the validated intent size and global 5 MiB policy. The 3 MiB threshold in the follow-up was a test intent, not a new global limit. No temporary waiver of input <=5 MiB is accepted. Production integration must reverify this policy.
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

### Approved follow-up decisions

1. **Unreferenced READY media is accepted in V1.** A successful upload may remain READY even if the Product form is abandoned or Product save fails. Do not add TTL or automatic cleanup of READY assets without a reliable Product–Media attachment contract.
2. **Completion after deletion never resurrects an asset.** For an owned intent, return a per-item `DELETED`/`UNAVAILABLE` outcome while retained state proves the asset is deleted or unavailable; return `NOT_FOUND` if the necessary lookup information has been purged. Recheck current asset state under a row lock or equivalent conditional transition when finalizing. Idempotency means no additional side effects, not an unconditional READY response.
3. **Intent ownership is approved.** Only the initiating principal UUID, still possessing `media:upload`, may complete an intent. Status is also limited to the initiating principal plus the appropriate permission; the exact status permission mapping remains to be specified. No administrator override in V1. For both batch complete and batch status, another principal's intent returns the same per-item `NOT_FOUND` outcome as a missing intent. Shared READY-asset read/delete policies remain unchanged.
4. **Continue A1 with signed eval.** The input-byte spike passed for the tested per-intent bound and request tampering cases. Backend-generated signed eval is the selected input-size mechanism; continue evaluating the remaining original-dimension/pixel, canonicalization and output gates. A2 is not selected or required by the current input-size evidence. Any future change of approach or D2/D4 policy requires a new user decision.
5. **Batch completion is bounded and permits partial success.** Enforce bounded inspection concurrency, a per-inspection timeout and an overall request deadline, without holding database transactions over provider I/O. Initial benchmark candidates are at most 10 intents per batch, at most 3 concurrent inspections and an approximately 10-second response target; these are not official defaults or SLA commitments. Items not inspected before the deadline return `PENDING`/`RETRYABLE` and remain eligible for reconciliation. Select the exact per-inspection timeout and production limits after measurement.

### Upload lifecycle versus attachment lifecycle

Upload lifecycle answers whether bytes have been verified, the asset is READY, or the upload has expired/failed and requires cleanup. **READY means verified and available under Media authorization; it never means attached to a Product.** A completed intent is historical evidence of upload completion, not proof that its asset remains available forever.

Attachment lifecycle answers whether and how Product references the asset. Keep this as a separate Product–Media integration scope: reliable reference/attachment updates, deletion protection, recovery after partial cross-service failures, and orphan cleanup policy. Preserve the legacy lifecycle direction as a future design input, including temporary/active/deletion concepts where appropriate, but do not copy its monolith transaction model or add `MediaAttachmentStatus` to direct-upload V1.

No cross-service database reads, shared transaction or assumption that Product save and Media completion commit atomically. Product owns gallery references and semantics; the future attachment contract must define each service's obligations before introducing automatic orphan cleanup. Cleanup of expired, invalid or ambiguous uploads remains in direct-upload scope; cleanup of valid READY assets merely because they lack Product references does not.

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

Response: ordered per-intent results such as `READY(mediaId)`, `PENDING/RETRYABLE`, `REJECTED(errorCode)`, `DELETED`/`UNAVAILABLE`, or `NOT_FOUND`; define exact enum/error contract in OpenAPI. A valid batch request may return mixed results; malformed request, duplicate IDs, excessive batch size or missing batch-level authorization fail as request errors. Another principal's intent is indistinguishable from a missing intent: return per-item `NOT_FOUND`. Repeat completion returns the same media identity while the asset remains READY; deleted/unavailable assets never return READY or cause asset recreation. Request-level failures keep the common Problem Details contract. The batch result envelope, HTTP success status and safe item error shape still need an explicit contract decision; item outcomes must not hide malformed-request or batch-level authorization failures in HTTP 200. Provider I/O is never performed inside a long-lived DB transaction.

```http
POST /api/admin/media/upload-intents/status
Content-Type: application/json

{"intentIds":["intent-1","intent-2"]}
```

This is a proposed batch status shape, not a locked REST contract. Status reads database state and does not implicitly claim provider verification. It must reflect current asset availability rather than reporting READY from a historical completed intent after asset deletion. Enforce initiating-principal ownership and the appropriate permission; another principal's or missing intent returns per-item `NOT_FOUND`. Define maximum batch size and the exact status permission mapping.

### Example: ten Product images

- Batch create persists ten independent pending intents atomically and returns ten signed instructions.
- Browser uploads ten files to Cloudinary with bounded parallelism (e.g., 3 concurrent, a tuning example not a requirement).
- Browser batch-completes the uploads that reached Cloudinary. If eight verify, one is not yet visible and one is invalid, return eight READY plus two distinct item outcomes. Retry only unresolved intents; Product attaches only READY media IDs.
- Reconciliation covers cases where Cloudinary succeeded but browser never called complete.

## Security and provider flow

1. Browser calls Media via authenticated, CSRF-protected BFF. Media checks `media.api` scope/audience and `media:upload` permission, validates declared file properties, selects unguessable exact provider identities, persists all batch intents **before releasing any signed instructions**, then signs server-controlled upload parameters. Avoid returning partial usable signatures if persistence fails.
   Backend generates eval from a validated numeric limit, signs the eval together with the exact upload options, and retains the intent's policy/limit for verification. Never accept eval JavaScript from FE or expose a generic arbitrary-parameter signing endpoint. FE sends the returned signed instructions unchanged.
2. Browser uploads bytes and exact signed fields to approved Cloudinary HTTPS endpoint. Never send BFF cookies, OAuth access tokens or Cloudinary API secret to Cloudinary. API key is public identification, not secret.
3. Browser batch-completes via BFF. Media inspects the **server-stored** Cloudinary identity using its own credentials, verifies resource type, delivery type, provider asset identity, timestamps, dimensions, bytes, format, processing evidence and policy. Do not trust caller-provided Cloudinary URLs or metadata.
4. Restrict completing/status-reading intents to the initiating principal UUID plus operation permission. Complete requires `media:upload`; select the appropriate status permission before implementation. This ownership rule is approved, with no administrator override. Return per-item `NOT_FOUND` for non-owned intents. Existing shared READY read/delete authorization remains unchanged.
5. Each successful intent creates/finalizes its asset exactly once with trusted final metadata. Intent completion history must not override current asset availability. Conditional transitions/row locks serialize completion with expiry, deletion and cleanup; do not resurrect deleted/expired assets. Retry returns the same media identity when still READY, a deleted/unavailable outcome when retained state proves unavailability, or `NOT_FOUND` when lookup information is gone.
6. Keep separate **intent TTL**, **provider signature validity** and **completion/reconciliation grace**. Cloudinary upload signatures are documented as valid for approximately one hour; they are not one-time, not bound to the bytes, and application expiry does not revoke them. Use exact server-chosen identity and `overwrite=false`, bounded quotas, retained cleanup identity/tombstone through replay horizon, and reconcile late writes. Deleting while a signature is still usable can allow re-creation.
7. Cloudinary upload URL path's `resource_type` is not bound by this signature: exact-form spike requests successfully reused image authorization on the raw path. Signed eval protected the byte cap on tested raw requests but did not force image normalization or prevent raw object creation. Inspect and reject mismatches, and retain exact alternate-type cleanup identities, including provider-appended extensions; image-only lookup is insufficient. The complete alternate-type strategy remains to be finalized.
8. No implicit direct-to-multipart fallback on timeout. Recover the same intent first; avoid duplicate provider uploads after ambiguous responses.

## A1 design and current evidence

**Approved direction:** signed browser upload, backend-generated signed eval, Cloudinary **incoming transformation**, `authenticated` delivery, trusted server-side inspection, a minimal signed-notification boundary and bounded reconciliation. Incoming transformation processes the stored original; eager derivatives alone do not establish canonicalization of that original. Keep synchronous processing where sufficient; do not add eager variants or an asynchronous processing state machine without a demonstrated need.

**A2 is not selected.** It remains historical alternative context, not an implementation phase, automatic fallback or prerequisite. The user has approved continuing A1 without waiving the input limit. If another gate cannot be satisfied, report its evidence and options; do not silently switch architecture or relax policy.

Evidence comes from the [initial A1 spike](../.agent-work/implementations/12-a1-spike.md) and [signed-eval follow-up](../.agent-work/implementations/12-a1-eval-spike.md). These are local evidence artifacts, not production acceptance reports. The first spike's simple normalization-plus-final-inspection configuration lost original input-size evidence; the subsequent signed-eval configuration closes that specific gap. Its NO-GO must not be read as a rejection of enhanced A1.

### Input byte policy — approved, integration revalidation required

- Validate each declared intent `sizeBytes` as a positive, bounded integer no greater than 5 MiB; reject invalid declarations at batch-create rather than silently accepting a larger declaration. Use an effective numeric cap no greater than both the validated intent size and global policy. Store the policy version and effective bound with the intent.
- Generate eval from server-owned code and validated integer values. Do not interpolate untrusted scripts or arbitrary strings. Include eval and fixed incoming/storage options in the signed parameter set; no generic FE-controlled signing API.
- Eval must **fail closed** for missing, wrong-type, non-finite, non-integer or non-positive original byte metadata, and reject bytes above the effective cap. Missing or malformed required verification metadata also prevents READY; provider/transient failures remain distinguishable from a definitive invalid asset.
- The successful spike used a simple numeric upper-bound check and `throw new Error('INTENT_SIZE_EXCEEDED')`. It did **not** separately exercise all missing/wrong-type metadata branches; those fail-closed branches remain required integration tests, not claimed PASS evidence.
- The 3 MiB valid PNG, 3 MiB +1 PNG and naturally oversized noisy PNG demonstrate that eval sees original bytes and can reject before the tested runtime transformation path. Signed-parameter tampering cases failed. This is not exact byte equality, hash/content binding, one-use authorization, or an ingress streaming limit; Cloudinary still receives data and obtains resource information.
- FE validation is for UX only. Media verifies the stored identity and trusted processing/policy evidence on completion; browser declarations, an upload response, or an unverified webhook do not establish compliance. The trusted source-evidence and policy-version representation for completion still needs to be specified and tested.

### Gate status — PASS applies only to stated observations

| Gate | Evidence and status | Still required before production |
| --- | --- | --- |
| Original input bytes | **PASS, tested per-intent bound:** exactly 3,145,728 bytes accepted; 3,145,729 and 3,148,927 bytes rejected with no image asset. Provider context exposed original bytes while normalized output was 3,422 bytes | Integrate validated intent/global limits; 5 MiB boundary tests; fail-closed metadata; reject-path persistence checks; production configuration revalidation |
| Signed-eval integrity | **PASS, tested mutations:** removing/replacing eval, increasing threshold, changing signed options, fake context and duplicate eval did not bypass validation | Exact production signing/transport and error handling; additional parser/endpoint cases as warranted. No claim of exhaustive bypass proof |
| Original dimensions/pixels | **NOT PROVEN:** initial normalization accepted 10001x1 and 5000x4001 | Evaluate server-owned signed checks against trusted original width/height and pixel product before resize; test 10k-side and 20 MP boundaries, invalid metadata and orientation. Do not infer originals from resized dimensions |
| Canonicalization | **PARTIAL PASS:** sampled EXIF orientation=6 JPEG changed 120x80 to 80x120; downloaded bytes lacked EXIF and synthetic comment markers | Broader JPEG/PNG corpus: EXIF GPS/XMP/IPTC/ICC, orientation variants, re-encoding evidence, animation/corrupt/disguised-format policy; establish equivalence to D2. Do not treat `fl_strip_profile` alone as proof |
| Original format/corruption | **PARTIAL observation:** initial GIF-disguised-as-JPEG and truncated PNG produced provider errors; precise error reason was not retained | Trusted original-format enforcement before any conversion can mask it; retain sanitized rejection evidence and verify asset absence |
| Final output cap | **NOT PROVEN as enforcement:** noisy PNG produced 4,328,276 final bytes, above 4 MiB | Reject verified output >4 MiB before READY; inspect exact final identity and clean rejected objects. Quality settings are not a hard cap; do not change rejection policy into silent resizing |
| Private delivery | **PASS, sample/account:** anonymous original and derivative both returned 401 | Revalidate actual production account/options and representative original/derived URLs; no public delivery fallback |
| Expiring original download | **PASS, sampled mechanism:** same 60-second private-download URL returned 200 before expiry and 401 after expiry +5 seconds | Production account/key/plan verification; this does not imply every signed CDN URL expires |
| Identity and inspection | **PASS, sampled identities:** initial 12 accepted image fixtures matched inspected public ID, asset ID, resource type and delivery type; eval context showed source bytes | Specify full required metadata, policy binding, timestamp checks and missing/mismatch behavior under concurrency |
| Replay | **RISK CONFIRMED:** same exact signature can repeat and recreate a deleted object; old exact-form timestamps were rejected | Keep replay-horizon tombstones, idempotent complete, late-write reconciliation and deletion-safe conditional transitions. Signed eval is not a replay solution |
| Alternate resource types | **RISK CONFIRMED:** image authorization accepted on raw path; byte guard still rejected oversized raw input, but valid raw input persisted | Bounded exact-identity cleanup across applicable resource types/extensions, reject non-image finalization, and validate discovery/late-write coverage |
| Batch inspection budget | **MEASURED once:** 10 independent assets / 3 workers completed provider inspection in 3,208 ms | Deadline/queue saturation tests including DB, BFF and finalization; retain 10/3/about-10s as benchmark candidates, not defaults/SLA |
| Webhook and browser integration | **NOT TESTED:** no real webhook delivery or browser/BFF/CORS spike | Verify exact notification type, signing key, raw-body signature/timestamp handling, retries, duplicate/out-of-order callbacks and real browser flow |

### Remaining A1 validation sequence

1. Revalidate the integrated input-byte guard, including strict numeric/fail-closed branches and intent/global boundary cases; do not reopen the selected enforcement mechanism without contradictory evidence.
2. Extend the isolated policy investigation to trusted **original** dimensions/pixels and format before normalization; keep resize from hiding invalid input.
3. Evaluate canonicalization on a representative corpus and specify verified-output rejection at 4 MiB. Prefer incoming transformation plus the smallest signed policy that satisfies the evidence; do not add a custom processing framework.
4. Validate alternate-type cleanup and replay horizons, then the minimal webhook path and completion/reconciliation races. Preserve the successful private-delivery observations while rechecking the final combined configuration.

No new live tests are run by this document update. The remaining isolated investigations follow the sequence above; all production implementation remains gated on the final proposal.

Cloudinary references consulted for this proposal:

- https://cloudinary.com/documentation/authentication_signatures
- https://cloudinary.com/documentation/image_upload_api_reference
- https://cloudinary.com/documentation/upload_parameters
- https://cloudinary.com/documentation/control_access_to_media
- https://cloudinary.com/documentation/notifications
- https://cloudinary.com/documentation/notification_signatures

## Minimal persistence and reconciliation

Prefer a separate additive `media_upload_intents` table because final `media_assets` metadata is required. Store identity, initiating principal UUID, deadlines, status, provider identity, necessary signature/processing context, retry bookkeeping and the stable completed media identity. Do not store secrets or fake final metadata. Use only as many states as needed: pending, completed, expired/rejected (and internal cleanup tracking as needed); avoid copying legacy attachment/derived-processing state machines. Resolve completion/status responses against current asset state; a stored completed identity must never be treated as an immutable READY result after deletion. Define tombstone retention and lookup/purge behavior before implementation; purge must not allow an old intent to be recreated or finalized again.

A **single bounded scheduled reconciliation worker** is sufficient initially, integrated with existing durable cleanup:
- Find due pending/uncertain intents and inspect exact Cloudinary identity outside DB transactions.
- Valid asset and still eligible: finalize with conditional state transition.
- Not yet found: retry within grace; after safe authorization/replay horizon, expire and clean up.
- Transient provider errors: bounded backoff; invalid or late asset: reject/cleanup without making READY.
- Keep identity/tombstone long enough to discover late writes. Coordinate concurrent complete, reconcile and cleanup; never delete a verified READY asset due to stale work.
- Inspect/reconcile the exact applicable resource-type/identity combinations, including provider extension variants established by tests. Keep durable cleanup evidence for invalid alternate-type objects; image not-found is not evidence that raw/video objects are absent. Never delete by prefix or trust an arbitrary callback-provided cleanup target.
- Valid READY media remains retained even if Product never references it. This worker must not infer attachment state or perform READY orphan cleanup. That policy belongs to a later Product–Media attachment contract.

### Minimal webhook role — proposed A1 integration

Use a provider-signed upload notification as an additional recovery signal. Configure/sign a server-owned `notification_url` in issued upload instructions when this path is adopted; do not accept an FE-selected callback URL or add eager derivatives solely to obtain callbacks. The exact notification event/path/configuration and live delivery behavior remain unverified contract details.

- Put the callback HTTP boundary under `asset/adapter/web` and Cloudinary signature verification in the technical adapter boundary. It is a provider-to-Media interaction, separate from browser/BFF authentication. A narrowly scoped callback route authenticates the Cloudinary notification, not a browser JWT; all current admin routes retain their existing authorization.
- Verify `X-Cld-Signature` and `X-Cld-Timestamp` against the original raw body using the supported SDK mechanism, with bounded payload size, an explicit timestamp window/clock policy and the correct notification signing key. Do not assume the upload key is always the notification key, or verify a reserialized JSON body. Choose the window with retry behavior in mind rather than copying the legacy default blindly.
- After verification and correlation to an existing persisted intent/provider identity, commit a small durable update that makes that intent due for inspection. Return success only after that work commits; transient persistence failure must remain retryable. Avoid storing full payloads/secrets or relying on an in-memory queue after acknowledgment. No new broker, generic inbox/outbox framework or independent processing engine is required by this proposal.
- Duplicate or out-of-order notifications are safe, idempotent signals. Unknown identities never create assets/intents; completed, rejected, expired or deleted state cannot be reverted. Specify acknowledgement behavior for ignored events and exact transient-failure statuses in the callback contract.
- The notification is not sufficient to mark READY, even when its signature is valid. The bounded worker inspects trusted provider state and calls the same per-intent verification/finalization used by complete. Browser complete can succeed without waiting for a webhook; periodic reconciliation repairs missed/exhausted notifications. Lost callbacks cannot strand an otherwise recoverable upload.

This fits a single existing-style reconciliation worker with persisted due/retry bookkeeping. Reuse principles from the legacy signature verifier, callback boundary, reconciliation request budgets/backoff and locked state rechecks; adapt their placement and contracts to the monorepo. Do not copy the legacy eager-only handler, public delivery assumptions, direct infrastructure imports in application code, attachment state machine or monolith transaction coupling.

### Batch completion execution budget

The overall deadline covers admission/queue wait, provider inspection, conditional finalization and response assembly. Bound concurrency across requests as well as within a batch so multiple callers cannot create unbounded provider work; exact process-wide capacity and fairness remain operational decisions. Keep independent per-item transactions and preserve successful item results when another item times out.

Do not start additional inspections after the request budget is exhausted. Bound each inspection by its configured timeout and remaining request budget, reserving time for finalization/response. An unfinished or unstarted item returns `PENDING`/`RETRYABLE`; reconciliation and subsequent complete calls must resolve its authoritative state. Caller timeout or attempted task cancellation does not prove a provider operation stopped. Define handling of in-flight work explicitly, with conditional state transitions preventing stale writes from restoring unavailable assets. The approximately 10-second target includes local work and is not a promise that all 10 items finish in one request.

## Compatibility constraints identified against current architecture

- `CloudinaryStorage` currently uses a 30-second provider wait bound, four workers and a queue of sixteen; `MediaStorage.inspect` has no caller deadline parameter. Reusing it unchanged cannot establish the proposed approximately 10-second batch response target. Plan a scoped deadline-aware direct-inspection boundary and resource budget after benchmarking; do not globally lower the existing multipart timeout or change its behavior incidentally.
- `MediaStorage.StoredAsset` and the non-null final fields in `media_assets` do not yet support pending intent metadata/verification. The additive intent design and trusted inspection data must address this without rewriting V1 migrations or weakening multipart validation.
- The current service does not expose a provider callback boundary. The proposed webhook adds a narrowly scoped signature-authenticated ingress and durable intent scheduling, not a global authentication bypass. Its raw-body handling, response contract, key configuration and retry semantics need explicit design and verification; existing REST/security behavior remains intact.
- The current HTTP error convention covers request-level failures and does not yet define a mixed-result batch envelope. Specify that envelope explicitly in Media OpenAPI while preserving common Problem Details for request errors; do not change the monorepo error policy implicitly.
- Current READY read/delete authorization is shared. New ownership applies to intent workflow endpoints only; it must not spread to existing asset endpoints or require Identity claim/permission changes by accident.
- BFF/session transport and Product attachment integration are absent. Browser production integration depends on the BFF; attachment lifecycle, READY orphan cleanup and Storefront delivery remain separate work. No change to service ownership, shared database access or distributed transaction scope is proposed.

Do **not** build a generic reconciliation framework, distributed orchestration engine, multi-queue processing system or generic media library for V1. Reconciliation repairs DB/provider divergence; it does not itself canonicalize images.

## Implementation slices after feasibility decision

1. Complete remaining A1 evidence in the validation sequence above. Preserve signed-eval input-cap approval; test strict numeric/fail-closed integration, original dimensions/pixels/format, canonicalization, verified output cap, alternate-type cleanup and notification behavior. Record failed/inconclusive gates without automatically selecting A2 or changing D2/D4. Select exact production limits only after representative benchmarking.
2. Additive OpenAPI contracts for batch create (1..N, atomic), batch complete (1..N, per-item results), batch status, and intent persistence. Specify approved ownership, non-owned `NOT_FOUND`, post-deletion outcomes and exact status permission; finalize limits/error enums/HTTP envelope and deadline behavior. Preserve current JWT claims, Identity entitlements and existing Media routes.
3. After the proposal is approved for implementation, implement application `createBatch`, `completeOne` (internal reusable operation), `completeBatch`, `getStatuses`; server-owned numeric policy/eval generation, Cloudinary signing/inspection and minimal intent persistence. Short DB transactions; no provider I/O under locks. Store the effective policy and verify trusted metadata before READY.
4. Implement bounded reconciliation and exact alternate-type cleanup, the minimal signed-webhook scheduling boundary, authorization, batch execution budgets and concurrency protections. Webhook/complete/worker converge on shared verification and conditional finalization. Retain original multipart behavior, preserve READY assets without Product references and keep attachment/orphan policy out of this worker.
5. Integrate Backoffice when real Product upload screen and BFF session/CSRF transport exist. Browser never holds OAuth token or provider secret. No category-specific workflow or universal media library.
6. Document provider configuration, D2/D4 guarantees, expiry/replay limitations, operations and both upload paths.

## Minimum verification

- Existing multipart image/security/lifecycle tests unchanged and green.
- Batch create: 1 and N items, invalid item rejects whole batch, duplicate client IDs, batch size/quota, authorization, atomic persistence before signing.
- Batch complete: mixed READY/retryable/rejected outcomes, repeated completion stable, duplicate/concurrent calls, no DB transaction held over provider I/O, per-item ownership and information-leak protection.
- Batch budgets: slow/hung inspection, executor saturation, overlapping batches, queue time and overall deadline; unstarted/unfinished items return retryable results without rolling back successes or preventing reconciliation. Benchmark 10 intents / 3 concurrent inspections / approximately 10 seconds; these values are candidates, not acceptance SLA values.
- Completion/status after deletion: return retained-state `DELETED`/`UNAVAILABLE` or purged-state `NOT_FOUND`; never return READY from cached completion history, recreate an asset, or let stale complete/reconciliation resurrect it. Cover concurrent delete/complete and shared READY delete by a different authorized admin.
- Batch status: pending/ready/error results, cross-admin access checks, missing IDs and bounded list.
- Authorization: principal UUID and current endpoint permission, no override; missing and non-owned intents are indistinguishable. Preserve shared READY-asset read/delete rules.
- Abandoned Product save: verified media remains READY; cleanup does not delete it based on age or absent Product references. No Product table access or attachment-state inference.
- Provider fixtures: signed field constraints, authenticated delivery, `overwrite=false`, forged metadata and identity/type/format/size/dimension mismatches.
- Signed eval: backend-only generation from validated numeric intent/global bounds, fail-closed metadata, original byte/dimension/pixel/format boundaries as the final policy evolves, unchanged-signature tampering, and trusted source versus normalized output evidence. Add real-account revalidation of the production policy; mock signatures alone do not prove enforcement.
- Verified output cap: no READY or download link for an inspected final asset >4 MiB; durable rejection cleanup, including webhook/complete races. Do not assume incoming quality parameters guarantee the cap.
- Webhook: original-body signature and timestamp/key checks, payload bounds, duplicate/out-of-order/unknown events, DB failure before acknowledgement, lost notifications recovered by polling, and no state resurrection. Exact generated alternate-type identities/extensions remain eligible for cleanup across late writes.
- PostgreSQL/concurrency: complete vs reconcile/expiry/delete, stale worker cannot resurrect; provider success with lost response; retryable inspect failure; late/replayed uploads and cleanup.
- Browser/BFF when available: no OAuth token/cookie/API secret sent to Cloudinary, bounded parallelism, partial batch recovery, stale file/session result protection, CORS.
- Real Cloudinary smoke must pass the feasibility gates; mocks cannot prove normalization or private delivery.

## Legacy reference — reuse principles, not behavior

Inspected `XdpsX/ecommerce-be`: `CloudinaryUploadAuthorizer`, `MediaUploadIntentService`, `MediaDirectUploadService`, `MediaDirectUploadPersistence`, `MediaUploadedAssetValidator`, `CloudinaryMediaAssetInspector`, `MediaReconciliationService`; and legacy Backoffice category upload hooks. For this update, rechecked the develop versions of `MediaReconciliationService`, `CloudinaryWebhookController`, `NotificationSignatureVerifier` and `CloudinaryNotificationSignatureVerifier` as targeted references, not a full security review.

Reuse: persist intent before exposing signed fields, inspect provider identity, idempotent completion, short DB transactions, verified raw-body notifications, bounded reconciliation requests, backoff and state rechecks. Do **not** inherit GIF support, public `type=upload` delivery, category purposes/attachments, eager-only callback semantics, shared ownership rules or an oversized processing framework. Legacy inspection does not prove canonicalization or D4 authenticated delivery.

References:
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryUploadAuthorizer.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/upload/MediaDirectUploadService.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/application/processing/MediaReconciliationService.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/api/CloudinaryWebhookController.java
- https://github.com/XdpsX/ecommerce-be/blob/develop/src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryNotificationSignatureVerifier.java

## Explicitly deferred / open decisions

- Deferred V1: public cancel, separate single-item endpoints, batch-create idempotency key/generic idempotency framework, generic Media Library, batch-level rollback after provider upload and broad frontend abstractions. Product–Media attachment contract, reference/deletion protection and READY orphan cleanup are separate future integration scope; preserve the lifecycle direction without implementing the old monolith attachment model here.
- Open before implementation: remaining A1 original-dimension/pixel/format and canonicalization evidence; strict fail-closed metadata behavior and trusted policy/source-evidence representation; verified output rejection/cleanup; exact combined provider configuration and production account revalidation. Signed-eval input-byte enforcement itself is selected, not an unresolved A1/A2 choice.
- Other open design details: alternate-resource discovery/cleanup and replay horizon; minimal webhook event/path/response/key/timestamp contract; exact batch maxima (create/complete/status), aggregate/per-admin quotas, concurrency/timeout/deadline budgets; OpenAPI item status/error shapes and HTTP envelope; permission for intent status; intent TTL, completion/reconciliation grace and tombstone/result retention/purge. Private-download expiry passed in the sampled account/mechanism; revalidate the deployed account rather than treating all signed URLs as expiring. Initial 10 intents / 3 inspections / approximately 10 seconds remain benchmark inputs only.
- If a remaining A1 gate fails or is inconclusive, report it for a user decision. A2 is neither selected nor an automatic next phase; no temporary input <=5 MiB exception or other D2/D4 relaxation is approved.
- Scheduling/integration open: whether this expansion becomes a separate CR/issue and when BFF/real frontend integration is scheduled. Storefront delivery and Product–Media attachment/orphan policy remain decisions for their separate integration scope, not direct-upload V1 blockers.
- Closed decisions: continue A1 with backend-generated signed eval for validated per-intent/global input bytes, retain D2 without an input-limit waiver, no A2 selection, unreferenced READY retention/no READY TTL, deletion-safe completion idempotency, initiating-admin ownership/no override/non-owned `NOT_FOUND`, and bounded partial-success batch completion. Replay and resource-type control remain separate requirements. Do not reopen these decisions as interchangeable implementation options.
- **No application code, migrations, Cloudinary runtime tests or frontend wiring are authorized by this document update.**
