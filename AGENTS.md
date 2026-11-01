# Agent instructions

This is the Ecommerce monorepo. Read `docs/06-development/workflow.md` and `docs/06-development/conventions.md` before changing code. Consult the relevant application README and local instructions when present; do not assume all applications use the same stack.

- Bootstrap stage: `services/` contains backend services and `web/` contains frontends. Do not bulk-migrate business code during bootstrap.
- Keep changes scoped to the approved task; preserve unrelated changes and avoid speculative infrastructure or abstractions.
- Identify affected applications, ownership and integration contracts before cross-service changes.
- Run proportional, application-specific verification and report exact commands, skipped checks and residual risks.
- Do not create branches, commits, push or open PRs unless explicitly requested.

Use `$cr-plan`, `$cr-implement`, `$cr-review` for change-request stages and `$architecture-review` for design trade-offs. Planning and review remain read-only.

Code intelligence is optional: see `docs/06-development/code-intelligence.md` for GitNexus and Serena setup. Skills must work without MCP by inspecting source directly; never treat graph results as authoritative without source verification. Use `$codebase-exploration` for tracing unfamiliar flows.

## Skills and artifacts

Read `docs/06-development/agent-skills.md` for skill routing and `docs/06-development/agent-artifacts.md` for Markdown output and retention. `$manual-test-cases` creates table-based QA cases. After completing an approved implementation, `$cr-implement` writes a beginner-friendly code-reading walkthrough in `.agent-work/implementations/<cr-id>.md`. Never commit `.agent-work/`.
