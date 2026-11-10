CREATE TABLE media_upload_intents (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    client_file_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    declared_content_type VARCHAR(64) NOT NULL,
    declared_size_bytes BIGINT NOT NULL CHECK (declared_size_bytes BETWEEN 1 AND 5242880),
    policy_version VARCHAR(32) NOT NULL,
    provider_public_id VARCHAR(512) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    media_id UUID UNIQUE REFERENCES media_assets(id),
    source_bytes BIGINT,
    source_width INTEGER,
    source_height INTEGER,
    source_format VARCHAR(16),
    source_evidence TEXT,
    provider_asset_id VARCHAR(128),
    final_bytes BIGINT,
    final_width INTEGER,
    final_height INTEGER,
    final_format VARCHAR(16),
    final_resource_type VARCHAR(32),
    final_delivery_type VARCHAR(32),
    result_status VARCHAR(24),
    error_code VARCHAR(64),
    last_error_category VARCHAR(64),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_recovery_claim_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    discovery_due_at TIMESTAMPTZ NOT NULL,
    discovery_cursor TEXT,
    discovery_resource_type VARCHAR(16) NOT NULL DEFAULT 'image' CHECK (discovery_resource_type IN ('image', 'raw', 'video')),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK ((state = 'COMPLETED') = (media_id IS NOT NULL))
);

CREATE INDEX media_upload_intents_owner_created_idx
    ON media_upload_intents(owner_id, created_at DESC);
CREATE INDEX media_upload_intents_recovery_due_idx
    ON media_upload_intents(next_attempt_at, last_recovery_claim_at, lease_until, created_at)
    WHERE state IN ('PENDING', 'REJECTED', 'EXPIRED', 'COMPLETED');
CREATE INDEX media_upload_intents_discovery_due_idx
    ON media_upload_intents(discovery_due_at, lease_until, created_at)
    WHERE state IN ('PENDING', 'REJECTED', 'EXPIRED', 'COMPLETED');

CREATE TABLE media_upload_discovered_objects (
    id BIGSERIAL PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES media_upload_intents(id),
    resource_type VARCHAR(16) NOT NULL,
    delivery_type VARCHAR(32) NOT NULL CHECK (delivery_type = 'authenticated'),
    public_id VARCHAR(512) NOT NULL,
    asset_id VARCHAR(128),
    state VARCHAR(16) NOT NULL CHECK (state IN ('DISCOVERED', 'DELETE_PENDING', 'DELETED', 'RETRY')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_category VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    UNIQUE (intent_id, resource_type, public_id)
);

CREATE INDEX media_upload_discovered_objects_due_idx
    ON media_upload_discovered_objects(next_attempt_at, id)
    WHERE state IN ('DISCOVERED', 'DELETE_PENDING', 'RETRY');

CREATE TABLE media_upload_quota_counters (
    scope_key VARCHAR(64) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    reserved_count INTEGER NOT NULL DEFAULT 0 CHECK (reserved_count >= 0),
    PRIMARY KEY (scope_key, period_start)
);

CREATE TABLE media_upload_provider_hourly_budget (
    hour_start TIMESTAMPTZ PRIMARY KEY,
    call_count INTEGER NOT NULL DEFAULT 0 CHECK (call_count >= 0),
    recovery_call_count INTEGER NOT NULL DEFAULT 0 CHECK (recovery_call_count >= 0)
);

CREATE TABLE media_upload_controls (
    control_key VARCHAR(64) PRIMARY KEY,
    control_value VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO media_upload_controls(control_key, control_value, updated_at)
VALUES ('issuance_enabled', 'true', CURRENT_TIMESTAMP);
