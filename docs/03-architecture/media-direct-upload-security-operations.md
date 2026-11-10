# Media direct upload: security and operations

## Status and ownership

As of 2026-11-06: **feasibility investigation CLOSED; A1 architecture direction SELECTED; backend implementation APPROVED; production enablement PENDING RELEASE VERIFICATION; residual risks DOCUMENTED with the V1 mitigations selected below.** [ADR-0003](adr/0003-media-direct-upload-cloudinary-a1.md) records the decision and alternatives. No further Cloudinary configuration exploration is scheduled in this phase.

This document defines direct-upload invariants and operational acceptance requirements. The backend API, additive persistence, verification, reconciliation and optional callback are implemented; production enablement remains pending release verification. Current multipart behavior is described in [Media Service](media-service.md) and the [service README](../../services/media/README.md). OpenAPI describes the implemented HTTP contract.

Media owns bytes, provider identities, upload intents and recovery. Product owns references, gallery order and primary-image selection. READY means verified upload availability, never Product attachment. Valid READY images without Product references remain retained; attachment protection and READY orphan cleanup require a separate integration contract.

## D2/D4: evidence, exceptions and open policy

The baseline is JPEG/PNG input <=5 MiB, <=10,000 px per side, <=20 megapixels, orientation-aware decode/re-encode with metadata removal, and final output <=4 MiB (D2); authenticated/private originals and derivatives with authorized short-lived original download access (D4). Existing Java multipart canonicalization remains unchanged. **Approved direct-upload V1 policies (2026-11-06):** accept observed JPEG COM, PNG text and provenance metadata retention, and exclude APNG. This differs from multipart Java; it does not establish equivalent canonicalization or turn the failed gate into PASS. Retained metadata may contain private information exposed in authorized downloads; authenticated delivery does not sanitize it. Keep provider stripping as best effort. No input-size waiver or relaxation of dimension/output/private-delivery requirements is approved.

| Requirement / risk | Isolated-account observation | Required policy or implementation condition |
| --- | --- | --- |
| Original bytes | Signed eval accepted exactly 3 MiB, rejected +1 byte and naturally oversized PNG; 5 MiB boundary also passed. Tested client guard mutations failed signature validation | Server generates eval from a validated integer bounded by intent and global limits; browser cannot supply script. Fail closed on absent, non-numeric or invalid metadata; revalidate deployed policy |
| Original dimensions/pixels | Ordinary JPEG/PNG boundary controls passed, including rejection before a resize could hide oversized source dimensions | Verify positive finite integer source dimensions and pixel bounds; not a guarantee of pre-decode provider resource protection |
| Metadata / canonicalization | Tested strip orders, force-strip, combined flags, quality and output-format candidates retain private JPEG COM or PNG text; provenance metadata also remains. Java reference removes these markers | **FAIL against original stripping gate; residual metadata ACCEPTED for direct V1.** Preserve the negative finding, privacy implications and best-effort stripping. No Java-equivalence claim |
| APNG | Relaxed controls flatten the first frame, but source dimensions were absent from tested eval metadata and explicit format lists rejected the fixture. Java accepts/flattens the tested APNG | **Source validation UNPROVEN; APNG explicitly EXCLUDED from direct V1.** Backend must reject before READY regardless of filename/MIME declarations or normalized static PNG output. Frontend checks alone are insufficient; multipart remains unchanged |
| Orientation / format / profiles | Eight EXIF orientations, transparency and selected grayscale/profile samples matched reference pixels; representative disguised/invalid formats rejected. Decoder acceptance differs on a damaged PNG CRC | Partial coverage, not universal decoder/color equivalence; test approved corpus against deployed configuration |
| Output and identity | Inspection distinguishes output above 4 MiB; expected provider identity/type matched observed image fixtures | Before READY, independently verify trusted final bytes <=4 MiB, positive dimensions within policy, allowed format, asset/public ID, image resource type and authenticated delivery type. Missing/mismatched data rejects or remains unavailable; browser responses never authorize READY |
| Private delivery | Tested anonymous original/derivative requests returned 401; a private original-download URL returned 200 then 401 after 60-second expiry | Revalidate deployed account and exact link mechanism; arbitrary signed CDN URLs are not necessarily expiring. Issued links are bearer capabilities, not revocable sessions |
| Replay | Same authorization recreated an object with a new asset ID after deletion | Signed eval and overwrite=false are not single-use protection. Retain intent history, prevent state resurrection, and reconcile late writes; the selected V1 budgets and retained-state recovery below apply |
| Raw fanout | Same authorization accepted on raw path; filename extensions created multiple objects. Fixed public ID suffix and signed format=png did not stop it. Tested eval resource-type fields were undefined | No resource-path binding claim. Exact-intent-prefix discovery found sampled variants, but finite sample listing is not an abuse bound; design bounded pagination, quotas and durable recovery |
| Webhook | Offline raw-body/signature/timestamp/key mechanics passed; live delivery/retry not tested because no receiver was available | Signed notification is a scheduling hint only. Durable acknowledgement, idempotency and reconciliation remain release tests |

Source-byte eval is not ingress streaming enforcement, exact file/hash binding or authorization revocation. Provider inspection of normalized output cannot reconstruct missing original properties. Any trusted source-evidence representation and policy version binding must be specified during implementation design.

## Intended lifecycle and security invariants

- Persist an atomic batch of independent intents before exposing signed fields. Use an additive intent model; do not fabricate final metadata or rewrite existing migrations. Define effective policy/limit, owner UUID, exact generated namespace and authorization timing durably.
- Only the creator, still holding media:upload, may complete an intent. Status also requires creator ownership and media:upload; another principal's intent is indistinguishable from missing (NOT_FOUND). Existing shared READY asset read/delete authorization stays unchanged.
- Route browser business requests through the authenticated, CSRF-protected BFF. No browser OAuth tokens, provider secrets, arbitrary signing or caller-selected notification URL. BFF/real-screen integration remains pending.
- Complete, webhook-triggered inspection and reconciliation use the same verification and conditional finalization. Do provider I/O outside DB transactions, then lock/recheck current state. Complete after deletion returns retained-state DELETED/UNAVAILABLE or purged-state NOT_FOUND, never a historical READY or recreated asset.
- Batch complete permits partial success with independent per-item commits. Bound process-wide and per-request concurrency, queue/admission time, per-inspection timeout and overall deadline. Unfinished items remain PENDING/RETRYABLE and recoverable. Selected defaults are ten intents, three inspections and a ten-second deadline as specified below; benchmarking is still required and no throughput SLA is implied.
- Verify callback signature over bounded original raw bytes with the correct notification key and explicit timestamp policy. Persist a due-for-inspection update before acknowledging success; transient DB failure remains retryable. Duplicate/out-of-order events cannot revert terminal state; unknown identities never create intents or supply cleanup targets.
- Use persisted leases/tokens, conditional updates and bounded retries/backoff so stale workers cannot finalize or delete a READY winner. Cancellation/timeouts and signature expiry do not prove remote writes have stopped.
- Discover alternate resource types and provider extension variants under the exact generated intent namespace. Validate returned ownership/identity before exact deletion. No folder/prefix deletion, whole-account scan or callback-selected arbitrary object deletion.

## Operations / runbook requirements

The following is the required operator procedure for the future direct-upload implementation. Instrumentation, configuration names, dashboards and commands must be supplied and tested with that implementation; none are claimed to exist today. Existing multipart cleanup remains governed by [Media Service](media-service.md).

### Suspected orphan, late write or alternate-type object

1. Correlate the persisted intent, allocated namespace, provider asset/public ID, type, policy and current asset state. Distinguish an abandoned/invalid intent from valid unreferenced READY media; the latter is not an orphan-cleanup target.
2. Inspect exact identities and, when needed, list only the validated intent prefix with bounded pages/request budget across applicable image/raw/video types. Preserve returned identity evidence and continuation state if the budget is exhausted; image not-found or one empty page is insufficient proof of absence.
3. Recheck DB state/lease eligibility before cleanup; delete only exact verified generated identities and record acknowledgements, sanitized failures and next retry. Protect READY winners against stale cleanup and handle timeout-after-provider-success as uncertain.
4. Reconcile again for late/replayed writes. Do not purge ambiguous tombstones based only on signature age or approximate one-hour validity. V1 retains all correlation/tombstone evidence without automatic purge and uses the recovery/escalation policy below.

### Quota, rate-limit or sustained fanout incident

Monitor provider storage/object counts, API/rate-limit consumption, intent issuance per admin, pending age, inspection/cleanup backlog, retries and saturated workers. Selected V1 application thresholds and budgets are specified below; provider dashboard thresholds must fit the deployed account quota. Per-intent signing quotas alone do not bound raw object count.

On abnormal growth, preserve identity evidence, contain new direct-upload issuance through an implementation-provided control, and budget reconciliation calls so recovery does not exhaust provider quotas. Do not disable existing multipart incidentally or mass-delete provider folders. Escalate ambiguous ownership, persistent credential errors, budget exhaustion and sustained late writes for operator review. No automatic tombstone purge or claim of bounded total abuse cost is approved.

### Missing, duplicate or failing webhooks

Verify callback reachability, configured notification key, raw-body handling, clock window and sanitized status counts. Never log signatures, secrets, full sensitive payloads or private URLs. Acknowledge only durable scheduling; retain retryable failures when persistence is unavailable. Duplicate notifications are expected and must be harmless. Periodic reconciliation must recover uploads even when callbacks are lost or exhausted; do not manually mark READY from a signed event alone.

### Rollout and recovery acceptance

Backend implementation is approved; keep production direct upload disabled until the release checks below are verified. Before rollout, test trusted verification and fail-closed metadata, output-cap rejection with no link/READY, permission/ownership, PostgreSQL complete/delete/reconcile races, stale leases, late/replayed writes, raw discovery pagination/budget exhaustion, quota containment and recovery after lost callbacks. Test live callback transport/retries with an approved receiver and private delivery/expiry on the deployed account. No tunnel or account-wide configuration change is implied.

Benchmark overlapping batches and timeout/saturation behavior; the observed 10 inspections with three workers in 3208 ms was provider-only, not an end-to-end SLA. Test browser/BFF/session/CSRF when that boundary exists. Keep current multipart regressions green and document concrete operator controls before production enablement.

## Implementation authorization and selected V1 design

The user has authorized backend implementation on the existing working branch and delegated the remaining V1 design choices. The following decisions replace earlier open design placeholders. They authorize code, additive migrations, OpenAPI updates and tests; production enablement remains conditional on release verification. No new issue, branch, commit, push, PR or merge is authorized. No additional Cloudinary configuration spike is required or authorized in this phase.

The defaults below are selected implementation settings, not measured capacity guarantees. They must be configurable and validated; record benchmark results without silently weakening security invariants. All new direct-upload controls are separate from the existing multipart configuration.

### HTTP contract

All three administrator endpoints require the existing issuer/audience/media.api scope checks, media:upload permission and a UUID principal. Ownership applies to complete/status; READY read/delete stays shared. Exact machine-readable schemas are to be added to Media OpenAPI during implementation.

| Endpoint | Request | Success |
| --- | --- | --- |
| POST /api/admin/media/upload-intents | Object with files array, 1..10 entries; each has clientFileId, filename, contentType, sizeBytes | 201, object with items array in request order: clientFileId, intentId, uploadUrl, fields (exact signed string values), expiresAt. No usable instructions until the complete batch is persisted |
| POST /api/admin/media/upload-intents/complete | Object with intentIds array, 1..10 distinct UUIDs | 200, object with ordered items; each has intentId, status, optional mediaId and optional errorCode |
| POST /api/admin/media/upload-intents/status | Same bounded ID shape as complete | 200, same item envelope; reads current DB/asset availability without implicitly performing provider inspection |

Reject duplicate clientFileId/intent IDs and invalid declarations as request-level 400. clientFileId is 1..64 characters, filename 1..255 characters (display only, never a key/path), MIME is image/jpeg or image/png, sizeBytes is integer 1..5,242,880; aggregate declared batch bytes <=52,428,800. Batch validation is atomic. Do not accept unknown provider/eval fields. Reject APNG declarations, but never rely on declarations alone for input acceptance.

Item status values are PENDING, READY, REJECTED, EXPIRED, DELETED, UNAVAILABLE, NOT_FOUND. PENDING has errorCode RETRYABLE when waiting for visibility, deadline, capacity or transient provider failure. READY alone includes mediaId. REJECTED uses safe codes INPUT_POLICY_REJECTED, OUTPUT_LIMIT_EXCEEDED, PROVIDER_IDENTITY_MISMATCH or UNSUPPORTED_INPUT. Unknown/missing/non-owned IDs all produce NOT_FOUND with no ownership detail. Terminal intent status never resurrects an asset; an existing asset in deletion/cleanup state produces UNAVAILABLE and a deleted asset produces DELETED. Only purged/missing correlation produces NOT_FOUND.

Malformed requests and request-wide errors use existing Problem Details: 400 validation, 401 unauthenticated, 403 unauthorized, 429 issuance/admission quota, 503 disabled or required infrastructure unavailable before item processing. 429 includes Retry-After. No 207 or provider exception leakage. Use Cache-Control: no-store for all new responses; do not log returned fields/signatures. Lost create responses may leave an extra batch; create idempotency remains deferred, complete idempotency is mandatory.

### Defaults, quotas and timing

| Setting | Selected V1 default |
| --- | --- |
| Feature / issuance | Direct upload disabled by default; separate issuance switch can stop new signatures while recovery remains active |
| Intent completion window | 15 minutes from creation; no finalization after expiry, even if remote authorization still works |
| New intents | 100 per principal and 1,000 globally per UTC day; batch reserves all slots atomically |
| Active intents | 20 per principal, 200 globally, counting nonterminal intents; terminal tombstones do not consume active slots |
| Batch inspection | At most 3 in flight per request and 3 process-wide direct provider operations; no unbounded executor queue |
| Inspection timeout | 3 seconds or remaining request budget, whichever is smaller; the 10-second batch deadline includes database lock waits. Bound claim, provider-budget reservation, finalization and retry/rejection row-lock waits to the remaining budget, reserve the last second for finalization/serialization, and recalculate before provider inspection and each completion/retry/rejection state update. If retry/rejection cannot persist before the budget expires, retain the lease for reconciliation |
| Admission | No blocking admission queue; unavailable slots yield per-item PENDING/RETRYABLE, not unbounded submitted futures |
| Direct provider budget | 120 direct-upload calls per UTC hour across instances, including inspection/list/delete, with the last 30 reserved for recovery. Persist counters atomically; count failed/timeout calls too. Existing multipart calls are not charged to this budget. Keep below deployed account headroom before enabling issuance |
| Reconciliation | Tick every minute; claim at most 10 due intents per tick. Lease 2 minutes, fresh token on reclaim; provider I/O stays outside transactions |
| Per-intent discovery | At most 3 pages of 100 objects per claim across applicable resource types; at most 10 exact deletes per claim, further limited by the shared hourly budget. Persist discovered targets and progress before continuing; resume on next claim |
| Retry | Exponential backoff with jitter, starting at 1 minute, capped at 1 hour; provider Retry-After takes precedence when longer |
| Retention/purge | No automatic intent/tombstone/result purge in V1, including completed and deleted correlations |

Quotas and provider budget must remain correct across concurrent requests/instances using PostgreSQL counters/locks scoped to Media; no new Redis/broker dependency or generic quota framework. Provider budget exhaustion leaves work pending with next retry, never discards it. Worker fairness must prevent one large raw namespace starving all others. The existing multipart executor/timeouts and quota behavior are unchanged; direct work uses a separate bounded execution boundary so its deadlines do not alter multipart semantics.

Expired/rejected/deleted intents retain namespace evidence indefinitely. First 24 hours after creation, scan eligible non-READY namespaces at hourly intervals (initial pending completion inspection still runs each due tick); thereafter scan daily under budgets. Completed intents also receive a daily alternate-type sweep, protecting their exact READY image winner. These intervals are recovery cadence, not proof of a safe replay horizon. If a scan is interrupted or has exhausted a budget, persist an immediate continuation due time rather than waiting for the next full sweep. A continuation must eventually restart from the first page to catch writes appearing before its cursor.

Repeated late writes, discovery budget overflow or provider 429/authentication failures produce sanitized operator-visible categories and stop new issuance when the backlog exceeds 200 cleanup-required intents or the oldest due recovery work is more than 24 hours overdue. Recovery stays enabled; restore issuance only through explicit operator action/configuration after investigation. Add health/status reporting and counters without exposing admin/intent IDs as metric labels. For cloud object-count/storage quota, use provider dashboard alerts; do not invent an account quota or poll the whole account per intent.

Replay/raw fanout is accepted as a residual limitation of this V1 architecture with retained identities, bounded discovery and issuance containment. These measures do not cryptographically prevent alternate uploads or guarantee a hard bound on remote storage cost. Production release requires validating these controls against the deployment's provider budget; this is not another prerequisite approval to begin coding.

### Policy evidence and state transitions

Store a policy version (initially direct-a1-v1), validated declared upper bound, effective global limits, owner UUID, generated intent/media UUIDs, exact provider namespace, signing timestamp, completion expiry, state/version, due/retry/lease fields and observed provider evidence. Use a separate additive intent table and, when necessary, a small durable discovered-object table; do not alter old migrations or relax media_assets constraints. Creating the media row and linking the intent must be one short transaction with trusted final metadata; no fake UPLOADING row with invented bytes/dimensions.

Use a new direct namespace under the configured Media folder with an unguessable UUID per intent and an unambiguous delimiter. Discovery results must match that namespace boundary, expected account/delivery type and generated ownership, not merely a textual prefix which could include another intent. Track resource type plus returned public ID and asset ID. Deletion targets are verified/discovered provider identities, never browser or webhook arbitrary paths.

Generate a fixed signed eval template from validated integers. It validates positive finite integer original bytes/dimensions, original jpg/jpeg/png format, 5 MiB/per-intent bound, side and pixel limits, and rejects unsupported/ambiguous source metadata. Have eval replace provider context with a fixed schema carrying policy version, intent identity and original bytes/dimensions/format. Retrieve this evidence independently from the provider during complete/reconciliation; do not use a browser upload response or callback as verification. Test that client field mutations cannot replace this evidence under the issued signature. No context values contain secrets.

APNG exclusion is a fail-closed implementation requirement: retain strict original metadata/format validation that rejects the observed APNG cases, test renamed files and normalization controls, and never infer original static PNG eligibility from final PNG bytes alone. If provider evidence is insufficient for a candidate input, leave it unavailable/reject it; do not silently accept or add A2. Any demonstrated bypass is a concrete release blocker, not a reason to refuse all other approved implementation slices or demand a fresh plan approval.

Persist source evidence and final inspection as verification results bound to the current intent policy and provider asset identity. Final bytes/dimensions/format, authenticated image type, public/asset identity and source evidence must all pass before READY. A missing object is retryable until expiry; known policy/identity violations reject with durable cleanup; unavailable provider service leaves PENDING until expiry. Expiry is terminal and never makes a late upload READY.

Use consistent locking: intent row first, then linked asset row when present. Complete/status/reconcile recheck the current asset state. Existing asset deletion can retain its asset-only transaction; it must not acquire intent locks in reverse order. Finalize by inserting/linking once under the intent lock and unique constraints, with expiry/state/version/lease rechecks. No provider call while holding these locks. Cleanup claims distinguish the approved READY image winner from other resource variants and cannot delete the winner via a stale intent claim. Webhook and polling only schedule the same shared verifier.

### Callback contract and rollout

POST /api/media/cloudinary/notifications is the sole new provider callback, with a separately scoped security chain authenticating the Cloudinary raw-body signature. It is not a JWT admin endpoint or a global permit-all bypass. Limit raw body to 64 KiB, verify with the configured notification key using SDK semantics, and accept timestamps at most 5 minutes old or 30 seconds in the future. Never widen the window to repair delivery; polling recovers delayed callbacks.

Respond 204 after durable scheduling of a known matching upload notification; return 204 for authenticated unknown/unsupported/duplicate/terminal events with no creation or arbitrary cleanup. Invalid JSON is 400, invalid/missing signature or timestamp is 401, oversized payload is 413, transient DB failure is 503. Missing body/correlation fields cannot schedule anything. Do not verify reserialized JSON. Require a server-owned HTTPS notification URL/key when webhook mode is enabled; without an approved receiver keep webhook mode off and rely on polling in local tests. Issued instructions never accept an FE-selected callback URL.

Backend implementation and offline/fixture tests can proceed without a BFF or live callback receiver. BFF/FE screen, Product attachments and storefront delivery are outside this backend handoff, not reasons to refuse it. Before production enablement: review regressions/architecture checks, verify deployed policy/private delivery/output rejection, demonstrate APNG exclusion or keep affected input support unavailable, test live notifications with an approved receiver if enabling webhook mode, and exercise concurrency/quota/cleanup recovery. Keep direct upload disabled until these checks are reported; do not present remaining release checks as a missing authorization to write code.
