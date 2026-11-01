---
name: manual-test-cases
description: Generate practical, executable manual QA test cases in Markdown tables, one row per case, based on approved requirements and actual application behavior. Use when asked for manual test cases, QA scenarios, acceptance test design, or regression test documentation.
---

# Generate manual test cases

Read root `AGENTS.md`, `docs/06-development/agent-artifacts.md` and `docs/qa/manual/README.md`. Inspect relevant requirements, API contracts, UI flows and source code; do not invent business rules.

1. Identify feature, CR, actor, scope, assumptions and environment.
2. Trace the relevant UI/API behavior and authorization boundaries; distinguish observed behavior from intended requirements.
3. Generate independent, runnable scenarios covering the happy path and meaningful validation, boundary, authorization and failure cases proportionally.
4. Write a Markdown file with feature metadata and **one test case per table row**. Columns: `ID | Scenario | Preconditions | Steps | Test data | Expected result | Priority | Type`. Use concise numbered steps separated by semicolons inside the cell; escape pipe characters.
5. Prefer observable expected outcomes, stable IDs, realistic but non-sensitive test data and clear setup/cleanup requirements.
6. Note gaps and unanswered requirements separately; never disguise assumptions as confirmed expected behavior.

## Output and retention

Default output: `.agent-work/test-cases/<feature>.md` (local, gitignored). If user explicitly requests a reviewed reusable test suite for the repository, write to `docs/qa/manual/<feature>.md` after confirming intended requirements; that file can be committed with approval. Do not commit or push without authorization.

Report generated file path, scenario count, coverage categories and unresolved questions. This skill authors test specifications, not test execution results. Do not alter application source.
