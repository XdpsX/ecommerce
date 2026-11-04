CREATE TABLE refresh_token_families (
    family_id UUID PRIMARY KEY,
    authorization_id VARCHAR(100) NOT NULL UNIQUE REFERENCES oauth2_authorization(id),
    administrator_id UUID NOT NULL REFERENCES administrators(id),
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    current_token_digest VARCHAR(64),
    current_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);

CREATE TABLE refresh_token_history (
    token_digest VARCHAR(64) PRIMARY KEY CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    family_id UUID NOT NULL REFERENCES refresh_token_families(family_id),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    successor_digest VARCHAR(64),
    CHECK ((consumed_at IS NULL AND successor_digest IS NULL) OR (consumed_at IS NOT NULL AND successor_digest IS NOT NULL))
);

ALTER TABLE refresh_token_families
    ADD CONSTRAINT refresh_family_current_token_fk
    FOREIGN KEY (current_token_digest) REFERENCES refresh_token_history(token_digest);

ALTER TABLE refresh_token_history
    ADD CONSTRAINT refresh_history_successor_fk
    FOREIGN KEY (successor_digest) REFERENCES refresh_token_history(token_digest)
    DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX refresh_family_admin_client_idx ON refresh_token_families(administrator_id, registered_client_id);
CREATE INDEX refresh_family_status_expiry_idx ON refresh_token_families(status, current_expires_at);
CREATE INDEX refresh_history_family_issue_idx ON refresh_token_history(family_id, issued_at);
