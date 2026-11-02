---
name: docs-update
description: Make scoped, approved updates to durable Ecommerce documentation using verified repository evidence.
---

# Documentation update

Read `AGENTS.md`, `docs/06-development/workflow.md`, `docs/06-development/agent-artifacts.md`, and `docs/06-development/documentation.md`.

## When to use

Use only when the user has authorized documentation edits, or when an approved implementation requires updating affected durable docs. A request to audit alone is not permission to edit.

## Procedure

1. Confirm approved scope and identify the authoritative source: actual implementation/configuration for current behavior; approved architecture decisions for intended design; OpenAPI/schema files for contracts where applicable.
2. Inspect the relevant docs and source before editing. If they conflict, flag the decision rather than silently replacing architecture intent with current implementation.
3. Update the smallest set of durable Markdown files needed; keep terminology, relative links, commands and examples accurate. Do not copy ephemeral CR plans/reviews into tracked docs.
4. Validate internal links and paths, check Markdown formatting using existing repository tooling if available, and review the final diff. Never claim checks ran unless executed.
5. Summarize changed files, evidence, verification, remaining questions and any skipped checks.

## Boundaries

Do not change application code, dependencies, CI, architecture decisions, or unrelated docs merely to make a documentation update pass. Do not create branches, commit, push or open PRs without explicit authorization. Work without MCP using direct source inspection. Do not store secrets or personal information in examples.
