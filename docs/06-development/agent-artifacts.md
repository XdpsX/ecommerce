# Agent-generated Markdown artifacts

Separate **working notes** from **durable project knowledge**. Do not commit temporary AI-generated plans, reviews, QA drafts, or implementation walkthroughs. Keep them under `.agent-work/`, which is gitignored.

| Artifact | Path | Trigger | Commit? |
| --- | --- | --- | --- |
| CR plan | `.agent-work/plans/<cr-id>.md` | Nontrivial planning | No |
| CR review | `.agent-work/reviews/<cr-id>.md` | Full CR review | No |
| Code-reading walkthrough | `.agent-work/implementations/<cr-id>.md` | After every completed CR implementation | No |
| Draft manual test cases | `.agent-work/test-cases/<feature>.md` | QA scenario request | No |
| Approved manual QA suite | `docs/qa/manual/<feature>.md` | Human-approved reusable suite | Yes |
| Durable architecture decision | `docs/adr/<decision>.md` | Explicitly approved ADR | Yes |
| Agent usage conventions | `docs/06-development/` | Durable process change | Yes |

Use issue ID for `<cr-id>` where available (e.g. `123-category-filter`); otherwise use a short descriptive slug. Do not overwrite an existing artifact silently. Ensure paths exist before writing.

## Plan format

Title and CR reference; goal/scope; evidence with real paths; proposed implementation steps; verification; risks and unresolved decisions. Keep proportional to task size. Planning remains read-only with respect to application source.

## Review format

Findings first with severity, path/line, impact and recommended fix; then checks run, unanswered questions and remaining risks. Reviewing must not modify application source.

## Implementation code-reading walkthrough

Short and understandable to someone new to the codebase. Suggested sections:

1. **What changed?** One paragraph.
2. **Where to start reading:** Exact source file and function/class.
3. **Follow the flow:** Numbered sequence of real functions/files, explaining inputs, important decisions and outputs.
4. **Concrete example:** A realistic request/action and what happens.
5. **How to verify:** Relevant test files and exact commands.
6. **Caveats:** Only important assumptions or known limitations.

Avoid copying large code blocks, listing every changed file without explanation, or inventing paths. This document is a learning aid, not a status report. Always produce it after a completed implementation, even when no plan artifact was requested.

## Manual QA

Use `$manual-test-cases` and `docs/qa/manual/README.md`. One test case per Markdown table row. Draft locally; promote to a committed suite only after human review. Avoid storing passwords, tokens or personal data in examples.

## Git policy

A local Markdown file is not authorization to commit. Never commit `.agent-work/`. Only promote reviewed, reusable knowledge to tracked docs with explicit authorization.
