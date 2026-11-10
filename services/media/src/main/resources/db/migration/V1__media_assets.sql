CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    uploader_administrator_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'UPLOADING', 'READY', 'DELETE_PENDING', 'CLEANUP_PENDING', 'AMBIGUOUS_UPLOAD', 'DELETED'
    )),
    media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png')),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 4194304),
    width INTEGER NOT NULL CHECK (width BETWEEN 1 AND 10000),
    height INTEGER NOT NULL CHECK (height BETWEEN 1 AND 10000),
    provider_public_id VARCHAR(255) NOT NULL UNIQUE,
    provider_asset_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ,
    delete_requested_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    cleanup_attempts INTEGER NOT NULL DEFAULT 0 CHECK (cleanup_attempts >= 0),
    next_attempt_at TIMESTAMPTZ,
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    last_error_category VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((lease_id IS NULL) = (lease_until IS NULL)),
    CHECK (status <> 'READY' OR (provider_asset_id IS NOT NULL AND uploaded_at IS NOT NULL)),
    CHECK (status <> 'DELETED' OR deleted_at IS NOT NULL)
);

CREATE INDEX media_assets_cleanup_due_idx
    ON media_assets(status, next_attempt_at, lease_until, created_at)
    WHERE status IN ('DELETE_PENDING', 'CLEANUP_PENDING', 'AMBIGUOUS_UPLOAD', 'UPLOADING');
CREATE INDEX media_assets_uploader_created_idx ON media_assets(uploader_administrator_id, created_at DESC);
