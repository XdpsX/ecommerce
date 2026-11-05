# Machine-readable contracts

This directory is the canonical location for versioned, **machine-readable** interface definitions shared between services and applications.

- `openapi/`: OpenAPI specifications for HTTP APIs, suitable for validation, documentation and client generation.
- [`openapi/common/problem.yaml`](openapi/common/problem.yaml): shared REST error schemas and responses; service specifications reference them. See the [HTTP error policy](../docs/04-contracts/http-errors.md).
- [`openapi/media.yaml`](openapi/media.yaml): Media upload, metadata, short-lived download-link and asynchronous deletion contract. Maven `verify` in `services/media` parses this document and resolves the common error references.
- `events/`: Event schemas and machine-readable message contracts. Choose a format when approved.

Do not add speculative schemas for unimplemented APIs. Keep published definitions aligned with approved decisions and actual implementation; assess consumer compatibility before changing them.

**Boundary:** Human-readable API/event conventions, error semantics, versioning and compatibility policies belong in [`docs/04-contracts/`](../docs/04-contracts/README.md). Link to schemas rather than duplicating them. See [engineering conventions](../docs/06-development/conventions.md).
