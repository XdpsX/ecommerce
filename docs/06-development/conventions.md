# Engineering conventions

- Prefer incremental changes to unrelated rewrites. Existing code is evidence of current behavior, not automatically the target architecture.
- Respect application and service boundaries. Keep transport, business rules and persistence concerns separated where appropriate.
- Identify data ownership and producer/consumer compatibility before modifying HTTP APIs, events or shared schemas.
- Preserve externally observable behavior unless the approved scope explicitly changes it; document migration and rollout requirements.
- Avoid new dependencies, shared libraries or abstractions without a demonstrated need.
- Do not expose secrets, sensitive data or untrusted HTML; check authentication and authorization at the relevant boundary.
- Update durable documentation when architecture, contracts or developer commands change.

## Testing and verification

Follow [Testing strategy](testing.md) for test types, selection, naming, TDD, snapshot policy and CI expectations. Keep verification risk-proportionate and report exact commands, outcomes, skipped checks and residual risks. Do not run source-mutating format commands during read-only review.
