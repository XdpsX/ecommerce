---
name: cr-plan
description: Investigate and plan a change request across one or more monorepo applications without modifying code. Use for scope, affected-flow, contract, migration and verification planning.
---

# Plan a change request

Read the root `AGENTS.md`, `docs/06-development/workflow.md` and `docs/06-development/conventions.md`; inspect relevant application instructions.

1. Establish issue/request, acceptance criteria, integration branch and explicit out-of-scope items. Surface missing decisions rather than inventing business rules.
2. Inspect current behavior and relevant history with file-level evidence.
3. Identify affected applications and trace only relevant layers: backend transport/application/domain/persistence; frontend route/component/state/API client; or cross-service producer/consumer/contract/data ownership.
4. Assess compatibility, security, migrations, deployment sequencing and testing proportional to the change.
5. Separate required changes from optional follow-ups; propose ordered steps and likely files.

Remain read-only: do not edit files or create branches, commits, issues or PRs without separate authorization.

## Output

Lead with recommended scope and approach. Include evidence of current behavior, affected components and contracts, implementation steps, minimal meaningful tests, risks, assumptions and blocking questions. Avoid exhaustive checklists for small CRs.

## Optional code intelligence

Follow `docs/06-development/code-intelligence.md`. If GitNexus is available and its index is current, use its graph/impact tools to narrow investigation; verify conclusions against actual source and account for dynamic Spring wiring, HTTP/event boundaries and generated code. If Serena is available, use symbol lookup/references to validate relationships. Neither tool is required: fall back to repository search and direct file inspection, and disclose missing or stale tooling. Do not run setup, indexing, hooks or install dependencies automatically.
