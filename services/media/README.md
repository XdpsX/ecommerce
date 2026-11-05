# Media Service

Media stores and serves private administrator-managed JPEG and PNG assets. It owns the `media` database and Cloudinary object identities. It does not provide public catalog delivery, product attachments, ownership-based authorization or unused-image expiry.

## Code organization

The asset capability owns `asset/domain`, `asset/application` and `asset/adapter`. Application services, ports, models and exceptions are grouped by responsibility; JDBC, Cloudinary, scheduling and asset HTTP adapters stay under `asset/adapter`. Service-wide security and Problem Details handling live in root `adapter/web`, while `configuration` contains service properties and bean wiring. Tests mirror these packages.

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

`READY` assets do not expire automatically. Product/catalog attachment and safe deletion protection are not part of this service contract; consumers must define those rules before keeping media IDs as durable references.

## Verification

```powershell
cd services/media
.\mvnw.cmd -B --no-transfer-progress verify
```

The suite includes image validation, JWT, OpenAPI reference, PostgreSQL lifecycle and real Identity Authorization Code + PKCE to Media tests. The cross-service test starts the packaged Identity JAR and uses a storage fixture, so it verifies the token journey and Media authorization without claiming live-provider behavior. `CloudinaryStorageTest` separately exercises the Cloudinary SDK's HTTP requests against an isolated local server, including request options, signed-link expiry parameters and timeout configuration. Neither test proves that a real account denies unauthenticated original/derivative delivery or enforces URL expiry. Before production, smoke-test those controls and retry behavior with an isolated Cloudinary account.
