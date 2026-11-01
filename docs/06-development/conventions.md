# Engineering conventions

- Prefer incremental changes to unrelated rewrites. Existing code is evidence of current behavior, not automatically the target architecture.
- Respect application and service boundaries. Keep transport, business rules and persistence concerns separated where appropriate.
- Identify data ownership and producer/consumer compatibility before modifying HTTP APIs, events or shared schemas.
- Preserve externally observable behavior unless the approved scope explicitly changes it; document migration and rollout requirements.
- Avoid new dependencies, shared libraries or abstractions without a demonstrated need.
- Do not expose secrets, sensitive data or untrusted HTML; check authentication and authorization at the relevant boundary.
- Update durable documentation when architecture, contracts or developer commands change.

## Testing and verification

Choose a small, risk-proportionate set: (1) the representative main behavior, (2) one meaningful failure when relevant, and (3) a useful boundary case when it adds confidence. Do not force all three for trivial changes.

Start with focused tests, lint/format checks and builds appropriate to affected applications. Run broader integration or consumer checks when shared contracts or infrastructure change. Do not assume Maven, Liquibase or frontend tooling applies to every application. Do not run source-mutating format commands during read-only review. Report exact commands and any checks not run.
