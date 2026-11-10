# Contract conventions and policies

This directory documents **human-readable** interface decisions: HTTP API conventions, contract-level error semantics, event naming, schema evolution, versioning and backward compatibility.

Machine-readable OpenAPI and event schemas belong in [root `contracts/`](../../contracts/README.md), the canonical location for those artifacts. Link to them rather than copying specifications.

- [HTTP errors](http-errors.md): Problem Details, validation, status/code semantics and protocol boundaries.

Only document approved policies or implementation-backed behavior; label proposals explicitly. See [Architecture](../03-architecture/README.md) for service boundaries and [Flows](../05-flows/README.md) for temporal interactions.
