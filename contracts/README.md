# Machine-readable contracts

This directory is the canonical location for versioned, **machine-readable** interface definitions shared between services and applications.

- `openapi/`: OpenAPI specifications for HTTP APIs, suitable for validation, documentation and client generation.
- `events/`: Event schemas and machine-readable message contracts. Choose a format when approved.

Do not add speculative schemas for unimplemented APIs. Keep published definitions aligned with approved decisions and actual implementation; assess consumer compatibility before changing them.

**Boundary:** Human-readable API/event conventions, error semantics, versioning and compatibility policies belong in [`docs/04-contracts/`](../docs/04-contracts/README.md). Link to schemas rather than duplicating them. See [documentation governance](../docs/06-development/documentation.md).
