# Documentation governance and tooling

Documentation should explain durable project behavior and decisions, not duplicate temporary AI working notes. Keep updates proportional to the affected change.

## Sources of truth

- `AGENTS.md`: project-wide agent behavior and skill entry points.
- `docs/06-development/workflow.md`: CR lifecycle and approval boundaries.
- `docs/06-development/conventions.md`: engineering conventions.
- `docs/06-development/agent-artifacts.md`: local versus committed artifacts.
- Application README and checked-in build/configuration files: commands and current implementation details for that application.
- Approved ADRs: intended architectural decisions. A discrepancy with code requires investigation; do not silently rewrite an ADR.
- Checked-in API specifications and schemas, when present: documented contract evidence; verify generated output and implementation where relevant.

Prefer linking to an existing canonical document instead of maintaining competing copies. Do not describe planned behavior as already implemented.

## Skills

- `$docs-audit` performs read-only, evidence-backed checks for broken internal links, outdated instructions, missing documentation, and discrepancies between docs and implementation.
- `$docs-update` applies explicitly approved, scoped documentation changes and validates the affected links and examples.

Both skills work with ordinary repository search and file reads; MCP is optional. The audit does not automatically invoke an update.

## When documentation changes

During `$cr-plan`, identify durable documentation that may need updates. During `$cr-implement`, update affected docs only within the approved CR scope. During `$cr-review`, flag documentation drift with file-level evidence. A standalone docs audit is always read-only.

For changed HTTP APIs, events, schemas, service ownership, environment variables or developer commands, verify relevant documentation and consumers. Preserve compatibility notes when needed. Do not create an ADR without approval.

## Validation

1. Check that changed relative Markdown links and referenced repository paths resolve from their containing files.
2. Compare example commands and configuration keys with actual files and application tooling.
3. Inspect the documentation diff for unsupported claims, duplicate guidance, secrets and unrelated edits.
4. Use existing lint/link-check tooling if available; report the exact commands and outcomes. Do not claim external links were checked if no network validation occurred.

Initially these checks are manual and report-only. A future CI link checker should be a separate, reviewed change with clear scope and false-positive handling.

## Artifact policy

Store temporary audit findings under `.agent-work/reviews/` when a file is needed, never in committed docs. Promote only reviewed, reusable information to `docs/`, following `agent-artifacts.md`.
