# ADR-0003: Select Cloudinary A1 for direct Media upload

- **Status:** Accepted
- **Date:** 2026-11-06
- **Scope:** Browser-direct image upload alongside the existing Media multipart workflow
- **Implementation:** Backend implementation approved (2026-11-06); production enablement requires release verification. Feasibility closed with negative findings preserved

## Context

Backoffice should upload bytes directly to Cloudinary while Media owns authorization, intent state, verification and recovery. The existing backend multipart path remains supported and uses Java decode/re-encode canonicalization. Direct upload must not be described as having equivalent canonicalization without evidence.

Isolated-account probes established that backend-signed Cloudinary `eval` can enforce an original-byte upper bound before the tested runtime normalization path. Exactly 3 MiB was accepted, while 3 MiB +1 and a naturally oversized image were rejected; unsigned mutations of the guard failed. Global 5 MiB and ordinary JPEG/PNG original side/pixel boundary probes also passed. These observations do not establish a provider streaming/decoder resource bound or universal account guarantees.

Other gates did not pass: tested incoming transformations retain JPEG COM, PNG text and provenance metadata; APNG original dimensions were unavailable to the tested guard. Signed authorization can be replayed after deletion and reused on a raw upload path, including creation of multiple objects through filename extensions. See the durable [security and operations specification](../media-direct-upload-security-operations.md) for the evidence summary and acceptance conditions.

## Decision

1. Select **A1**: Cloudinary direct upload with incoming transformation, backend-generated signed `eval`, trusted provider inspection, minimal signed-webhook scheduling and durable reconciliation. Close feasibility exploration; do not continue trying provider configurations in this phase.
2. Generate eval only from validated numeric intent/global bounds. Never accept scripts from the browser or provide generic signing. Missing or invalid required metadata fails closed. No input <=5 MiB waiver is accepted.
3. Keep the existing backend multipart path unchanged, with no automatic fallback after an ambiguous direct upload. Preserve Media ownership, private authenticated delivery, and the Backoffice BFF boundary in [ADR-0001](0001-backoffice-bff-as-api-gateway.md).
4. Treat provider authorization as reusable, not single-use or resource-type-bound. Require alternate-type discovery/cleanup, idempotent completion and reconciliation; webhook receipt alone never establishes READY.
5. **Accepted policy clarification (2026-11-06):** direct-upload V1 may retain JPEG COM, PNG text and provenance metadata observed in the tested Cloudinary processing. This is an explicit metadata-removal exception to D2 for direct upload only, not a PASS for Java-equivalent canonicalization. Residual metadata can contain private information and may be included in authorized downloads; authenticated delivery does not remove it. Keep incoming stripping as a best-effort measure.
6. **Exclude APNG from direct-upload V1.** Do not flatten it into an accepted image or allow READY without proving supported original input properties. Backend enforcement and regression tests must cover APNG regardless of filename, MIME declaration or normalized output format; frontend checks alone are insufficient. Existing multipart Java behavior is unchanged.
7. Direction and these two policies are accepted, not every residual risk or production enablement. The user delegated remaining V1 choices; the security/operations specification selects quotas, retained tombstones, bounded recovery and contracts for implementation. Tests must establish their behavior before production enablement.

## Alternatives considered

- **Backend multipart only:** preserves current Java validation and metadata removal, but sends all upload bytes through Media and does not provide the requested browser-direct workflow. Retained as an independent supported path.
- **A1 incoming transformation plus final inspection only:** loses original-byte information during normalization; insufficient for the input bound. Signed eval closes that specific gap, not the remaining canonicalization or lifecycle gaps.
- **A1 with signed eval (selected):** uses provider processing and avoids adding backend source retrieval/reprocessing. Adds provider-specific policy and durable lifecycle work, with explicitly unresolved policy gates.
- **A2 private staging plus backend canonicalization:** could reuse Java processing but adds backend image I/O, staging identities and cleanup. Not selected; not an automatic response to a failed A1 gate.
- **Relax D2 implicitly:** rejected. Any precise metadata exception, input-format restriction or processing change needs explicit approval.

## Consequences and acceptance boundary

The reason to proceed with A1 as a direction is the demonstrated original-input policy mechanism and the preference for provider-side processing. The accepted limitation of this decision is that direction selection precedes production readiness. The explicitly accepted metadata exception covers the observed JPEG COM, PNG text and provenance retention; it does not waive byte/dimension/output limits or private delivery. APNG is excluded from direct-upload V1. Raw fanout/replay remains a residual risk with the selected V1 controls; neither a hard remote-cost bound nor cryptographic prevention is claimed.

Implementation must prove trusted output/identity verification, conditional state transitions, concurrency limits, durable cleanup and callback recovery. Production enablement additionally needs deployed-account validation and operational acceptance tests. The subsequent user authorization permits backend code, additive schema and HTTP contract changes described in the security/operations specification. It does not authorize deployment or unrelated integrations.

Upload readiness remains separate from consumer image references. Valid unreferenced READY assets are retained until explicitly deleted. **Subsequent consumer policy (2026-11-13, Issue #23):** Media may delete an asset independently of Ecommerce references; optional READY checks do not reserve it, and consumers display a placeholder when it is unavailable. Attachment reservation, reference tracking, deletion protection and Media Library UI are outside Issue #23. No shared database transaction or reference-based READY orphan cleanup is introduced. See the [consumer image-reference policy](../media-service.md#consumer-image-references). This clarification replaces the earlier deferred attachment-contract prerequisite without changing the A1 upload decision or its acceptance gates.

## Related documentation

- [Media Service](../media-service.md)
- [Direct-upload security and operations](../media-direct-upload-security-operations.md)

These durable documents preserve decisions and negative findings independently of disposable spike artifacts and temporary plans.
