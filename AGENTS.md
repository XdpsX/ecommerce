# Agent instructions

Read [engineering conventions](docs/06-development/conventions.md), [CR workflow](docs/06-development/workflow.md) and the affected application's README before working. Engineering, security, compatibility, documentation and testing rules live in conventions; do not duplicate them here.

## Agent-specific boundaries

- Work only within the approved scope. Preserve unrelated changes.
- Planning, review, exploration and documentation audits are read-only. Never infer permission to edit from an audit.
- Do not create or switch branches, commit, push, create issues or open PRs without explicit authorization. Skills do not grant permission.
- Use the smallest relevant skill from `.agents/skills/`: `$issue-create`, `$cr-plan`, `$cr-implement`, `$cr-review`, `$pr-create`, `$architecture-review`, `$codebase-exploration`, `$manual-test-cases`, `$docs-audit`, `$docs-update`, or `$impeccable` for UI work. Do not modify third-party skills or hooks without approval.
- Follow each skill's output format. Store temporary plans, reviews, QA drafts and implementation walkthroughs in ignored `.agent-work/`; never commit them. Only approved reusable QA suites and ADRs belong in tracked docs.
- GitNexus and Serena are optional; see [code intelligence](tooling/code-intelligence.md). Skills must work without MCP and verify graph findings against source. Do not automatically install or index tools.
