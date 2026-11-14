# Ecommerce Category management

Issue #23 adds administrator-managed categories to the Ecommerce modular monolith. The HTTP contract is [`contracts/openapi/ecommerce.yaml`](../../contracts/openapi/ecommerce.yaml); runtime setup and local database commands are in the [Ecommerce README](../../services/ecommerce/README.md).

## Ownership and persistence

Ecommerce owns category rows and their hierarchy. `imageAssetId` is only a UUID reference: there is no Media foreign key, remote lookup or delete call. Media may remove the referenced image at any time, so a consumer must handle missing metadata with a placeholder. Product references, attachment protection and storefront behavior are outside this capability.

Flyway V2 creates `categories` and the singleton `category_structure_state`. Hibernate is configured to validate the schema, never create or update it. Category metadata uses JPA `@Version`; the structure revision protects sibling ordering and parent relationships. All structural commands read a revision, validate the relevant tree, conditionally advance the revision, then persist within the same database transaction. Create performs at most three fresh transaction attempts and does not ask the client for a revision. Move, reorder and delete require the revision returned by the structure snapshot.

Sibling order is dense and zero-based. The database has a deferred uniqueness constraint for `(parent_id, display_order)`, treating null roots as one sibling group. Parent deletion is restricted; the API rejects categories with children. The generated slug is only a suggestion and is not reserved. Manual slugs are stable across rename and move unless explicitly changed.

## Authorization and API

Every Category route requires the `ecommerce.api` scope and the exact `category:read`, `category:write` or `category:delete` permission. Identity migration V7 seeds these permissions for `ecommerce-api` and assigns them to `SUPER_ADMIN`; permissions in already issued tokens do not change.

The API supports bounded list queries, individual reads, a consistent flat structure snapshot, slug suggestions, create/update, move, full sibling reorder and leaf delete. Administrator responses use `Cache-Control: no-store`. Errors use the shared Problem Details shape. Audit actors come from the validated JWT subject; clients cannot set IDs or audit metadata.

`ACTIVE`/`PUBLIC` visibility is computed through every ancestor for the structure response. Stored descendant flags are not rewritten when a parent changes. New categories start `INACTIVE` and `PRIVATE`; maximum tree depth defaults to eight and can be configured with `CATEGORY_MAX_DEPTH`.
