# Media Service

Media is a standalone Spring MVC resource server. It owns image metadata, generated Cloudinary object identities, upload/deletion operation state and cleanup retry records in the `media` database. Identity owns user accounts, permissions and token issuance. Media does not read Identity tables; Ecommerce and future consumers store stable Media UUIDs only after defining their own reference rules.

The `asset` feature groups its domain, application services, ports, models, exceptions and technical adapters. Service-wide security and HTTP problem handling live under root `adapter/web`; shared configuration remains under `configuration`.

## API and access

The service accepts one JPEG or PNG through `POST /api/admin/media`, returns metadata from `GET /api/admin/media/{id}`, creates a short-lived original-image link at `POST /api/admin/media/{id}/download-link`, and accepts asynchronous deletion at `DELETE /api/admin/media/{id}`. The contract is [`contracts/openapi/media.yaml`](../../contracts/openapi/media.yaml).

Every route requires a signed RS256 JWT with the configured Identity issuer, `media-api` audience, `media.api` scope and operation permission (`media:upload`, `media:read` or `media:delete`). Roles alone do not grant access. The `uploadedBy` UUID records the administrator who uploaded the image; all administrators with the required permission can read or delete assets in this single-shop model.

Cloudinary assets use the `authenticated` delivery type so originals and derived assets are protected. Media creates a signed, expiring original-download URL only after checking that the asset is READY. The URL is a bearer capability and must never be logged or stored. Issued links cannot be recalled before their expiry. Real-account verification of authenticated original/derivative denial and link expiry remains a release gate.

## Upload validation and lifecycle

Media keeps asset code under `asset/domain`, `asset/application` and `asset/adapter`. The controller lives in `asset/adapter/web`; application services, ports, models and semantic failures live under `asset/application`; provider, JDBC and cleanup integrations live under `asset/adapter`.

The service bounds multipart input to 5 MiB, checks JPEG/PNG signatures, reads image dimensions before pixel decode, limits each side to 10,000 pixels and total area to 20 megapixels, and uses a bounded decoder semaphore. It re-encodes decoded pixels to strip metadata and applies JPEG EXIF orientation before storage. Provider calls use a 30-second default wait bound, four concurrent workers and a bounded queue of sixteen; a timeout bounds Media's wait but cannot establish that Cloudinary cancelled an operation already received. The proposed output limit is configurable at 4 MiB; output size and decoder concurrency need load/resource benchmarking before production enablement.

Before provider I/O, Media commits an `UPLOADING` row with a UUID-generated object identity. Provider I/O runs outside the database transaction. Only a successful upload followed by an identity/type/format inspection can transition the row to `READY`. If the outcome is uncertain, the row remains a cleanup tombstone and is never reported as a successful upload.

Deletion first commits `DELETE_PENDING`; new links are denied immediately. A scheduled worker claims bounded rows with `FOR UPDATE SKIP LOCKED` and a lease, calls Cloudinary outside the database transaction, then conditionally records completion. An expired outstanding lease is eligible for reclaim independently of retry backoff and receives a new lease token; stale workers cannot complete work under the old token. Errors use exponential backoff with jitter capped by configuration. A definite not-found result completes ordinary deletion; for an uncertain upload, not-found is not proof that an in-flight upload cannot appear later, so the tombstone is retained and the exact object identity is reconciled again.

Cleanup attempts and sanitized error categories remain in the database for operator inspection. Investigate persistent pending rows, credential/rate-limit failures and old ambiguous-upload tombstones before considering any manual action. Do not purge tombstones or delete by folder/prefix. READY images are not removed automatically: there is no attachment/reference contract or unused-image TTL in this change. Any product/catalog consumer must establish deletion protection before persisting durable references.

## Local operation and verification

The local Compose initialization creates a separate `media` database on the existing PostgreSQL server. Existing volumes require running `init.sql` manually once. Start the service from the repository root with the `local` profile and provide the root `.env` values; valid Cloudinary credentials are required to start the production adapter. Maven `verify` runs real-PostgreSQL lifecycle ITs with Testcontainers and parses the OpenAPI contract with external common error references.

The Identity-to-Media integration fixture uses a storage fake to exercise the real token journey and Media authorization. A separate adapter test sends actual SDK requests to an isolated local HTTP server and checks provider request/response assumptions. Neither proves that a real Cloudinary account denies unauthenticated originals and derivatives or enforces URL expiry. Those checks require an isolated account and are not claimed as passed by automated CI.

## Direct upload backend

[ADR-0003](adr/0003-media-direct-upload-cloudinary-a1.md) selects Cloudinary A1 and closes feasibility investigation. The backend adds batch intent creation, owner-bound complete/status, independently verified finalization, bounded reconciliation and an optional signed-notification scheduling endpoint. The direct feature is disabled by default and production enablement remains subject to release verification. Existing multipart endpoints, Java canonicalization, persistence and cleanup behavior above remain unchanged.

The durable [direct-upload security and operations specification](media-direct-upload-security-operations.md) records D2/D4 evidence, accepted JPEG COM/PNG text/provenance metadata retention, APNG exclusion, signed eval requirements, replay/raw fanout residual risk and recovery limits. Direct uploads do not claim Java-equivalent canonicalization. READY means the authenticated provider asset passed source/final evidence checks; it does not mean Product attachment. Unreferenced READY assets are retained.

`MEDIA_DIRECT_UPLOAD_ENABLED=true` enables the direct API and recovery worker only after deployment review. Keep it false until the release checks pass. `MEDIA_DIRECT_UPLOAD_ISSUANCE_ENABLED=false` or the durable `media_upload_controls.issuance_enabled` latch can stop new signatures independently while recovery continues. Restore the database latch only after investigation. The implementation details and operator SQL are in the [service README](../../services/media/README.md#direct-upload-backend-disabled-by-default).

The hourly PostgreSQL provider-call counter applies to direct-upload inspection/discovery/deletion only. Thirty of the default 120 calls are reserved for direct-upload recovery. Existing multipart operations stay outside this counter and retain their current behavior, executor and timeout settings.
