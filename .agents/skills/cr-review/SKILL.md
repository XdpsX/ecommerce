---
name: cr-review
description: Perform a findings-first, read-only review of a completed monorepo change against its approved scope and integration base. Use for correctness, security, compatibility and regression assessment.
---

# Review a change request

Read root `AGENTS.md`, `docs/06-development/workflow.md`, `docs/06-development/conventions.md` and applicable application instructions.

1. Establish issue, acceptance criteria, approved plan and intended base. Review the complete branch diff, not only the latest commit; state when the base is uncertain.
2. Prioritize incorrect behavior, data integrity, concurrency, security/authorization, API and event compatibility, and unmet acceptance criteria.
3. Follow affected application boundaries: backend transactions/persistence/migrations where applicable; frontend state, rendering, accessibility and API error handling where applicable; producer/consumer contracts and rollout for cross-service changes.
4. Evaluate tests proportional to risk, generated artifacts and documentation drift. Inspect surrounding code to verify findings and run non-mutating checks when practical.
5. Distinguish confirmed defects from questions and optional improvements.

## Output

Report findings first in severity order, each with file/line, impact, evidence and actionable correction. Then open questions, verification performed and residual risks. Explicitly say when no findings were identified. Remain read-only; never modify files or commit as part of review.

## Optional code intelligence

Follow `docs/06-development/code-intelligence.md`. If GitNexus is available and its index is current, use its graph/impact tools to narrow investigation; verify conclusions against actual source and account for dynamic Spring wiring, HTTP/event boundaries and generated code. If Serena is available, use symbol lookup/references to validate relationships. Neither tool is required: fall back to repository search and direct file inspection, and disclose missing or stale tooling. Do not run setup, indexing, hooks or install dependencies automatically.

Use GitNexus impact results as leads, not proof; inspect affected callers/consumers and the complete diff. Review must not mutate source or run automatic indexing/setup.
