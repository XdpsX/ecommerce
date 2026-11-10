INSERT INTO permissions (id, code, audience) VALUES
    ('05f37b89-44ac-4214-b6a3-9c68f5c1f601', 'media:read', 'media-api'),
    ('257a80c0-7015-477f-9e9e-0d2cbb6d38a2', 'media:upload', 'media-api'),
    ('982f93fa-dc03-4dc5-9c4f-c917ef83ad8b', 'media:delete', 'media-api');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.audience = 'media-api';

ALTER TABLE administrator_audit ADD COLUMN target_client_id VARCHAR(100);
CREATE INDEX administrator_audit_target_client_time_idx
    ON administrator_audit(target_client_id, occurred_at DESC)
    WHERE target_client_id IS NOT NULL;
