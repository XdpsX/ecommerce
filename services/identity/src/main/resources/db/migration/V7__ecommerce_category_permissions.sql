INSERT INTO permissions (id, code, audience) VALUES
    ('2ca6a152-81af-474b-b51e-1d0cad014c31', 'category:read', 'ecommerce-api'),
    ('a801dc5e-80e8-4e29-a3e4-665f389af261', 'category:write', 'ecommerce-api'),
    ('43f1ad80-9cf6-4b5c-8b90-1335742bbc12', 'category:delete', 'ecommerce-api');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.audience = 'ecommerce-api' AND p.code LIKE 'category:%';
