# Engineering conventions

Canonical engineering rules for this monorepo. Agent-specific permissions and skill routing are in [AGENTS.md](../../AGENTS.md).

## Scope and boundaries

- Prefer incremental, approved changes to unrelated rewrites. Existing code is evidence of current behavior, not necessarily the target architecture.
- During bootstrap, `services/` holds backend skeletons and `web/` holds frontends. Do not bulk-migrate legacy business code incidentally.
- Respect application and service boundaries; separate transport, business rules and persistence concerns where appropriate.
- Identify affected applications, data ownership, producers, consumers and integration contracts before cross-service changes.
- Preserve observable behavior unless approved scope changes it; document migrations, compatibility and rollout requirements.
- Avoid speculative infrastructure, dependencies, shared libraries and abstractions without demonstrated need.

## Code organization

Follow [code organization](code-organization.md) for backend and frontend feature organization, responsibility and dependency boundaries, and justified exceptions. Apply it to new code and scoped refactors; internal packages and folders may vary by capability.

For all backend services, that guide defines the approved separation between root `config`, service-local technical `shared` responsibilities and feature-owned layers/adapters. Existing placements migrate within an approved scope; the convention alone does not authorize code changes.

## Security and contracts

- Do not expose secrets, sensitive data or untrusted HTML. Check authentication and authorization at relevant boundaries.
- Assess consumer compatibility before modifying HTTP APIs, events, shared schemas or configuration interfaces.
- Follow the [HTTP error contract](../04-contracts/http-errors.md) for REST error responses and protocol-specific exceptions. Map application/domain exceptions centrally with `@RestControllerAdvice` in HTTP adapters; controllers should not repeat `try/catch` solely to choose an HTTP status. Local catches are appropriate for a concrete recovery or endpoint-specific behavior. Security-filter failures need entry points/access-denied handlers; advice does not replace them.

## Documentation

- Update durable documentation when architecture, contracts, service ownership, environment variables or developer commands change, within approved scope.
- Application source and checked-in configuration evidence current implementation; approved ADRs document intent and may not yet be implemented. Do not silently rewrite an ADR to match code.
- Checked-in OpenAPI/event schemas define machine-readable contracts; verify generated output and consumers where applicable.
- Link to canonical documents instead of duplicating rules; clearly label proposed versus implemented behavior.
- Temporary AI plans, reviews and QA drafts belong in ignored `.agent-work/`. Promote only approved reusable knowledge to tracked `docs/`.
- Check changed relative Markdown links from their containing files; verify documented commands and configuration keys against source. Review for secrets, unsupported claims and unrelated edits. Report checks run and skipped; do not claim unverified external links were checked.
- Documentation audits are read-only. Use `$docs-audit` for findings and `$docs-update` only for approved changes. Do not create ADRs without approval.

## Testing and verification

Follow [testing strategy](testing.md) for test types, selection, naming, TDD, snapshots and CI expectations. Keep verification risk-proportionate; report exact commands, outcomes, skipped checks and residual risks. Do not run source-mutating formatting during read-only review.
