CREATE TABLE identity_security_state (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    bootstrap_completed_at TIMESTAMPTZ,
    bootstrap_administrator_id UUID REFERENCES administrators(id)
);
INSERT INTO identity_security_state(singleton_id) VALUES (1);

CREATE TABLE administrator_audit (
    id UUID PRIMARY KEY,
    actor_administrator_id UUID,
    target_administrator_id UUID,
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX administrator_audit_target_time_idx ON administrator_audit(target_administrator_id, occurred_at DESC);

ALTER TABLE administrators ADD CONSTRAINT administrators_normalized_username
    CHECK (username = lower(btrim(username)) AND length(username) > 0);
ALTER TABLE permissions ADD CONSTRAINT permissions_code_format
    CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$');

INSERT INTO roles (id, code) VALUES ('7a85c5f8-c2ae-45e6-bfd3-f203bdb3b018', 'SUPER_ADMIN');
INSERT INTO permissions (id, code, audience) VALUES
    ('3d3e8848-995d-43f0-ae3e-d333963c8b45', 'administrator:read', 'identity-api'),
    ('a41cfeb9-ef7a-4c6d-a207-d11bc0c4b731', 'administrator:write', 'identity-api'),
    ('25a7a5b7-1799-4d35-82e0-2db59b03ed67', 'entitlement:read', 'identity-api'),
    ('c88643e5-a979-43c5-bae2-9bc66e4b32d7', 'entitlement:write', 'identity-api');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.audience = 'identity-api';
