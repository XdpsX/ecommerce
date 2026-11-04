# Architecture Decision Records (ADRs)

ADRs capture approved architectural choices, their context, alternatives and consequences. They document decisions, not implementation status.

| ID | Decision | Status |
| --- | --- | --- |
| [0001](0001-backoffice-bff-as-api-gateway.md) | Adopt Spring Cloud Gateway as Backoffice BFF | Accepted |
| [0002](0002-admin-authorization-rbac.md) | Use RBAC with fine-grained permissions for admin APIs | Accepted |

## Conventions

- Use sequential four-digit IDs and descriptive kebab-case filenames.
- Statuses: **Proposed**, **Accepted**, **Superseded**, **Deprecated**.
- Prefer a new ADR that explicitly supersedes an accepted decision over silently rewriting its substance.
- Record implementation issues separately; an accepted ADR does not mean the feature exists.
