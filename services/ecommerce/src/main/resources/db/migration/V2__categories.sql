CREATE TABLE category_structure_state (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    revision BIGINT NOT NULL CHECK (revision >= 0)
);
INSERT INTO category_structure_state(singleton_id, revision) VALUES (1, 0);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(200) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 200),
    slug VARCHAR(200) NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description VARCHAR(4000),
    image_asset_id UUID,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    version BIGINT NOT NULL CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    CONSTRAINT categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT categories_slug_unique UNIQUE (slug),
    CONSTRAINT categories_sibling_order_unique UNIQUE NULLS NOT DISTINCT (parent_id, display_order)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX categories_parent_order_idx ON categories(parent_id, display_order);
CREATE INDEX categories_created_idx ON categories(created_at DESC, id ASC);
