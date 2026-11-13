# Media Service

Media stores and serves private administrator-managed JPEG and PNG assets. It owns the `media` database and Cloudinary object identities. It does not provide public catalog delivery, product attachments, ownership-based authorization or unused-image expiry.

## Code organization

The asset capability owns `asset/domain`, `asset/application` and `asset/adapter`. Application services, ports, models and exceptions are grouped by responsibility; JDBC, Cloudinary, scheduling and asset HTTP adapters stay under `asset/adapter`. Spring properties and bean/security composition live in root `config`. Service-wide Problem Details handling lives in `shared/error`; business exceptions remain in `asset/application/exception`. Tests mirror their implementation packages; `MediaJwtValidationTest` remains beside the configuration whose package-private bean it checks. No Cloudinary, persistence or feature HTTP component moves into `shared`.

## Local setup

From the repository root, copy `.env.example` to `.env`, set local PostgreSQL values, and provide credentials for an isolated Cloudinary environment. Media refuses to start its Cloudinary adapter without `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET`.

```powershell
docker compose --env-file .env -f infra/compose/compose.yml up -d postgres
.\services\media\mvnw.cmd -f services\media\pom.xml "-Dspring-boot.run.profiles=local" spring-boot:run
```

The `local` profile imports the root `.env`; do not copy it into this service. Compose creates the `media` database only for a fresh PostgreSQL volume. For an existing volume, follow the [infrastructure database setup](../../infra/README.md#identity-database) and run the mounted `init.sql` as the PostgreSQL owner. Never remove volumes to apply this migration.

Configure `MEDIA_DB_URL`, `MEDIA_DB_USERNAME`, `MEDIA_DB_PASSWORD` and `MEDIA_PORT` independently from Identity. The local database may use the existing PostgreSQL role; production credentials should be scoped to Media's database. Media uses `IDENTITY_ISSUER` and `IDENTITY_JWK_SET_URI` to validate RS256 access tokens. The configured issuer must match the token issuer exactly.

## API and security

See [`contracts/openapi/media.yaml`](../../contracts/openapi/media.yaml). Upload requires a token with `media.api` scope, `media-api` audience and `media:upload`. Metadata and download links require `media:read`; deletion requires `media:delete`. Roles alone do not grant access. The initial `SUPER_ADMIN` role receives all three permissions through Identity migration V6.

Identity's existing client registration mismatch protection remains enabled. For an existing confidential authorization-code client, explicitly add Media scope from an interactive terminal after applying Identity migrations:

```sh
java -jar services/identity/target/identity-0.0.1-SNAPSHOT.jar --identity.server-enabled=false --identity.command=add-media-scope --client-id=backoffice-bff
```

The command only adds `media.api`; it preserves client credentials, redirect URIs, grants and other scopes. Existing token grants do not acquire the scope or permissions; authorize again for a fresh token. The operator command writes an audit record.

## Upload and deletion behavior

Media accepts one JPEG/PNG at a time, up to 5 MiB, 20 megapixels and 10,000 pixels per side. It checks the byte signature and decoded image, applies JPEG EXIF orientation, strips metadata by re-encoding and limits canonical output bytes. Input bytes, output bytes, pixel limits and decode concurrency are configurable. The default output cap and single decode permit are proposed operational settings; benchmark them for the deployment budget before enabling production uploads.

Provider upload/inspection/delete calls have a configurable 30-second timeout by default. At most four provider calls execute concurrently, with a bounded queue of sixteen waiting calls; saturated or timed-out calls fail with a sanitized storage category. The timeout bounds the adapter's wait and does not prove a timed-out provider operation was cancelled; uncertain uploads therefore remain tombstones for reconciliation. The cleanup lease and abandoned-upload threshold must both exceed this timeout.

Assets are uploaded as Cloudinary `authenticated`; original and derived assets must remain inaccessible without signed authorization. Media returns a short-lived original download link (default 60 seconds), which acts as a bearer capability. Do not persist or log it. A link already issued remains usable until provider expiry even if the asset is later deleted.

An `UPLOADING` row is committed before provider I/O. Only a verified provider response and read-after-write inspection may move it to `READY`. A timeout or uncertain provider result leaves a durable tombstone for cleanup. Deletion commits `DELETE_PENDING` first and returns 202; cleanup claims rows under a lease and retries exact generated object identities. Persistent credential, quota, rate-limit or ambiguous-upload failures remain visible through the asset row's attempt count, next-attempt time and sanitized error category. Investigate these rows and preserve tombstones; never mass-delete by provider folder or purge ambiguous rows.

`READY` assets do not expire automatically. Under the approved [consumer image-reference policy](../../docs/03-architecture/media-service.md#consumer-image-references), Ecommerce stores `imageAssetId` independently; Media may delete assets without checking Category references. Optional READY checks at assignment do not reserve an asset. Consumers display placeholders for missing/unavailable images. Replacing/removing a reference or deleting a Category does not delete Media bytes. Attachment reservation, reference tracking, deletion protection and Media Library UI are outside Issue #23.

## Verification

```powershell
cd services/media
.\mvnw.cmd -B --no-transfer-progress verify
```

The suite includes image validation, JWT, OpenAPI reference, PostgreSQL lifecycle and real Identity Authorization Code + PKCE to Media tests. The cross-service test starts the packaged Identity JAR and uses a storage fixture, so it verifies the token journey and Media authorization without claiming live-provider behavior. `CloudinaryStorageTest` separately exercises the Cloudinary SDK's HTTP requests against an isolated local server, including request options, signed-link expiry parameters and timeout configuration. Neither test proves that a real account denies unauthenticated original/derivative delivery or enforces URL expiry. Before production, smoke-test those controls and retry behavior with an isolated Cloudinary account.

## Direct upload backend (disabled by default)

Cloudinary A1 is selected and feasibility investigation is closed under [ADR-0003](../../docs/03-architecture/adr/0003-media-direct-upload-cloudinary-a1.md). The backend contract, additive `V2__media_upload_intents.sql` persistence, signed instructions, independent provider verification, reconciliation and optional callback are implemented. Production enablement still awaits release verification. Current multipart endpoints, Java canonicalization and cleanup behavior remain unchanged.

The administrator API adds batch create, complete and status at `/api/admin/media/upload-intents`. They require the existing `media.api` token checks and `media:upload`; complete/status also require creator ownership. READY is based on independently inspected provider evidence. `POST /api/media/cloudinary/notifications` is a scheduling hint and never marks an asset READY. Direct upload is disabled unless `MEDIA_DIRECT_UPLOAD_ENABLED=true`; leave it false until the [release checks and operational limits](../../docs/03-architecture/media-direct-upload-security-operations.md) pass. Enabling it also requires Cloudinary credentials. Webhooks additionally require `MEDIA_DIRECT_UPLOAD_WEBHOOK_ENABLED=true`, a server-owned HTTPS `CLOUDINARY_NOTIFICATION_URL` and a separate `CLOUDINARY_NOTIFICATION_KEY`; otherwise recovery uses polling.

`MEDIA_DIRECT_UPLOAD_ISSUANCE_ENABLED=false` stops new signatures while the recovery worker remains active. The database has a second issuance latch. Inspect it and the sanitized recovery log counters before restoring issuance:

```sql
SELECT control_value, updated_at
FROM media_upload_controls
WHERE control_key = 'issuance_enabled';

SELECT hour_start, call_count, recovery_call_count
FROM media_upload_provider_hourly_budget
ORDER BY hour_start DESC
LIMIT 24;

-- Restore only after investigating the backlog and provider errors.
UPDATE media_upload_controls
SET control_value = 'true', updated_at = CURRENT_TIMESTAMP
WHERE control_key = 'issuance_enabled';
```

The recovery worker logs claimed/failed work, due backlog, issuance state and oldest due age without logging intent IDs, credentials, signed fields or callback bodies. No intent/tombstone purge is scheduled. READY without a Product reference remains retained; BFF/session/CSRF UI, Product attachments and storefront delivery remain separate scope. Automated tests use fixtures and PostgreSQL; they do not verify deployed Cloudinary policy, private delivery, expiry or live webhook retries.

The hourly PostgreSQL provider-call counter applies only to direct-upload inspection/discovery/deletion. Thirty of the default 120 calls are reserved for direct-upload recovery. Existing multipart upload, inspection and deletion remain outside this counter and retain their current behavior, executor and timeout settings.
