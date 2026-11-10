---
name: docs-audit
description: Read-only documentation consistency audit for the Ecommerce monorepo.
---

# Documentation audit

Audit documentation without changing files. Read `AGENTS.md`, `docs/06-development/workflow.md`, `AGENTS.md`, and `docs/06-development/conventions.md` first.

## When to use

Use when requested to audit docs, before a documentation migration, or when a CR changes architecture, API contracts, developer commands, or service ownership. For ordinary CR review, focus on documentation affected by the diff rather than scanning everything.

## Procedure

1. Define the target scope and identify the relevant docs and source/contract files. Prefer repository search and direct file reads; optional code intelligence is not required.
2. Check relative Markdown links and referenced paths against the repository. Do not treat external URLs as verified without actually checking them.
3. Compare documented commands, configuration, ownership, API contracts and architecture claims with authoritative current source/configuration. Distinguish actual implementation from intended decisions (e.g. approved ADRs).
4. Classify each finding as broken link, stale instruction, missing documentation, conflicting sources, or unverified claim. Report severity, exact paths/lines when available, supporting evidence, and suggested owner/action.
5. Mark uncertainty explicitly; do not invent missing files or assume a code/doc mismatch means the documentation must change.
6. Provide a concise summary of checked scope, findings, checks not performed, and residual risks.

## Output

Findings-first Markdown in the response. For a substantial audit, write a local report to `.agent-work/reviews/<scope>-docs-audit.md` if local filesystem access is available; never commit it. Audit is read-only: no edits, formatting, branch operations, commits, or PRs. Use `$docs-update` only after explicit approval of changes.
