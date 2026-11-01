---
name: cr-implement
description: Implement an approved monorepo change-request plan with proportional verification. Use when scope and intended behavior are sufficiently clear, not for exploratory planning or independent review.
---

# Implement a change request

Read root `AGENTS.md`, `docs/06-development/workflow.md`, `docs/06-development/conventions.md` and applicable application instructions.

1. Confirm the approved scope, acceptance criteria and intended branch; inspect working-tree status and preserve unrelated changes. Do not create or switch branches without authorization.
2. Identify affected applications and owners of data, APIs, events or shared contracts. Stop for a decision that would materially change behavior.
3. Implement the smallest coherent vertical slice. Update consumers, generated artifacts, migrations and durable docs only when required by the change.
4. Add representative tests covering the main behavior and meaningful failure or boundary risks when useful.
5. Run focused verification using commands appropriate to each affected application, then broader checks for shared contracts or cross-service changes.
6. Inspect the final diff for scope creep, compatibility changes, secrets, generated-file drift and formatting churn.

Explain material deviations from the approved plan before proceeding if they alter user-visible behavior; otherwise disclose them in the handoff.

## Output

Summarize implemented behavior, affected applications/contracts, decisions, exact verification commands and results, skipped checks and remaining risks. Do not commit, push or open a PR unless explicitly requested.

## Optional code intelligence

Follow `docs/06-development/code-intelligence.md`. If GitNexus is available and its index is current, use its graph/impact tools to narrow investigation; verify conclusions against actual source and account for dynamic Spring wiring, HTTP/event boundaries and generated code. If Serena is available, use symbol lookup/references to validate relationships. Neither tool is required: fall back to repository search and direct file inspection, and disclose missing or stale tooling. Do not run setup, indexing, hooks or install dependencies automatically.

For a well-scoped symbol edit, Serena may help locate and modify the intended symbol; inspect the resulting diff and run relevant tests. Prefer straightforward edits when safer. Do not let MCP tooling expand the approved scope.
