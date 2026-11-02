---
name: codebase-exploration
description: Explore and explain an unfamiliar Ecommerce code path, module, symbol or dependency chain with source-backed evidence. Read-only; optionally use GitNexus and Serena.
---

# Explore the codebase

Read root `AGENTS.md`, relevant application instructions and `docs/06-development/code-intelligence.md`.

1. Clarify the entry point and question (request, symbol, feature, dependency or suspected impact); identify which repository/application is actually present.
2. Use GitNexus graph tools when available and indexed to discover candidate definitions, callers, dependencies and impact. Check freshness before relying on the index.
3. Use Serena symbol navigation/references when available to inspect relevant definitions; otherwise use ordinary file search and reading.
4. Trace only the relevant path and confirm each important relationship in source code. Treat reflection, Spring injection, ORM, events and remote API calls as possible graph blind spots.
5. Explain the execution flow, file/symbol evidence, boundaries, uncertainty and suggested next investigation. Distinguish observed facts from inferred relationships.

Never edit files, index repositories, install tools, modify MCP settings, create commits or PRs. The skill works without either MCP tool; state limitations and use source-based fallback.

## Artifacts

Explain source-backed paths inline by default; do not write files unless explicitly requested.
