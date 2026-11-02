# Agent skills: responsibilities and routing

Use the smallest relevant skill. The root `AGENTS.md` owns project-wide rules, `workflow.md` owns the CR lifecycle, `conventions.md` owns engineering rules, and `code-intelligence.md` owns optional GitNexus/Serena setup. Skills should refer to these documents rather than duplicating them.

| Skill | When to use | Output |
| --- | --- | --- |
| `$issue-create` | Draft or create a CR issue using the repository issue template | Issue draft or verified GitHub Issue URL and metadata |
| `$pr-create` | Draft or open a PR using the repository PR template after implementation and review | PR draft or verified GitHub PR URL and base |
| `$cr-plan` | Scope and investigate a change before coding | Inline summary; local plan Markdown for nontrivial CR |
| `$cr-implement` | Implement an approved CR | Code and tests; always a local beginner code-reading walkthrough |
| `$cr-review` | Independent findings-first review | Inline findings; local Markdown for full CR |
| `$architecture-review` | Compare architecture options and ownership | Inline recommendation; ADR only if approved |
| `$codebase-exploration` | Trace unfamiliar symbols and flows | Source-backed explanation |
| `$manual-test-cases` | Design manual QA scenarios | Local Markdown table; optional reviewed committed suite |
| `$docs-audit` | Read-only checks for documentation drift, links and unsupported claims | Findings with source evidence; optional ignored local report |
| `$docs-update` | Approved, scoped changes to durable documentation | Updated docs, link verification and diff summary |
| `$impeccable` | UI design, accessibility, polish, critique | UI-specific results per upstream skill |

## Composition

For an approved new CR, use `$issue-create` to capture scope before `$cr-plan`; after implementation and `$cr-review`, use `$pr-create` for the PR handoff when authorized. Neither skill grants permission to create GitHub artifacts without explicit user instruction. For every CR, consult `testing.md`: plan selects risk-based checks from acceptance criteria; implementation adds appropriate tests (selective TDD); review checks behavioral coverage and reported results. For a backend CR, use plan → implement → review; call codebase exploration when needed. For a frontend UI CR, add Impeccable when the UI needs design work. For cross-service CRs, identify ownership and contracts before implementation. Generate manual test cases when QA coverage is requested or in acceptance criteria. Use `$docs-audit` to check documentation drift and `$docs-update` only when changes are authorized; see `documentation.md`.

## Rules

- Do not install or modify community skills without a separately approved task.
- Preserve Impeccable's upstream files and hook settings.
- GitNexus and Serena are optional; verify any inference in source.
- A skill does not grant permission to create branches, commit, push, or open PRs.
- Consult `agent-artifacts.md` for what to write and what to commit.
