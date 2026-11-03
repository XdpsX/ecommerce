# Code intelligence: GitNexus and Serena

GitNexus and Serena are **optional local developer tools**, not application build/runtime dependencies. Ecommerce skills remain usable without them. This guide is for Codex on a developer workstation; check upstream documentation for version-specific CLI changes.

## What each tool does

- **GitNexus**: local code indexing and knowledge graph for code navigation, callers, dependencies and impact analysis. Supports Java and TypeScript; static analysis may miss Spring runtime wiring, reflection, generated clients and cross-service HTTP/events.
- **Serena**: language-server-based symbol discovery, references and targeted editing. Language servers may need separate runtimes and initialization time.
- **Ecommerce skills**: `cr-plan`, `cr-implement`, `cr-review`, `architecture-review`, `codebase-exploration`. They own the workflow and use tools only when available.

## Prerequisites

Install Git, Node.js/npm/npx and Codex. For Serena, install `uv`/`uvx` (and a compatible Python runtime). Verify versions against the upstream README rather than relying on an unpinned global installation.

## GitNexus: index and connect

From the **repository root** in PowerShell:

```powershell
git status --short
npx gitnexus analyze --index-only
npx gitnexus doctor
```

Use `--index-only` to avoid GitNexus generating or overwriting our `AGENTS.md`, skills and hooks. Inspect `git status --short` afterward. Do not commit generated graph/cache data.

To register GitNexus MCP for Codex, choose **one** approach (not both):

```powershell
npx gitnexus setup -c codex
```

Or configure MCP manually:

```powershell
codex mcp add gitnexus -- npx -y gitnexus@latest mcp
```

Verify with `codex mcp list`, restart Codex and ask it to list available GitNexus tools and inspect an indexed symbol. A listed server alone does not prove it has started successfully.

If `doctor` reports LadybugDB FTS/BM25 unavailable, graph operations may still work while text search is degraded. With network access, follow the tool's repair guidance, e.g.:

```powershell
$env:GITNEXUS_LBUG_EXTENSION_INSTALL = "auto"
npx gitnexus analyze --repair-fts
npx gitnexus doctor
```

Index each repository you intend to inspect (for example the new monorepo and the legacy backend separately). Re-index after material code changes; verify freshness before making impact claims.

## Serena: connect to Codex

If you already installed the Serena CLI, use its supported setup command:

```powershell
serena setup codex
```

Alternatively, with `uvx` available, register the server without a separately installed `serena` executable:

```powershell
codex mcp add serena -- uvx -p 3.13 --from git+https://github.com/oraios/serena serena start-mcp-server --project-from-cwd --context codex
```

Use one method and confirm the command against the current Serena README. Check `codex mcp list`, restart Codex, then ask it to activate the current project and find a known symbol without editing. Start Codex from the intended repository root so `--project-from-cwd` points to the correct project. Java/TypeScript language-server availability may affect results.

## Suggested smoke test

1. Confirm `codex mcp list` includes the intended servers and that Codex can actually call their tools.
2. Ask `$codebase-exploration` to trace one existing Java or TypeScript symbol and cite source files.
3. Ask `$cr-plan` to estimate impact of a hypothetical change, **without modifying files**.
4. Compare graph results to actual source, including remote/API boundaries. Check `git status --short` for unexpected generated files.

## Troubleshooting and collaboration

- Missing MCP tools: restart Codex, check server launch commands and `codex mcp list`; inspect server logs.
- Missing symbols: verify project root, language-server readiness and GitNexus index freshness.
- Graph search limited: check `gitnexus doctor` for FTS/BM25 issues.
- Unwanted generated instructions: inspect the diff before removing or keeping them; prefer `analyze --index-only` in this repo.
- A teammate who clones Ecommerce does **not** automatically receive local MCP installations or caches. They can follow this guide, or work with source-search fallbacks. Do not commit machine-specific paths, secrets, caches or personal Codex configuration.

Official docs: [GitNexus](https://github.com/abhigyanpatwari/GitNexus) · [Serena](https://github.com/oraios/serena) · [Codex MCP](https://developers.openai.com/codex/mcp)
