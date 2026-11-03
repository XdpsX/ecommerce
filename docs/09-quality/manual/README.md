# Manual test case format

Store reusable, human-reviewed manual test suites here. Drafts belong in `.agent-work/test-cases/` and are not committed. Use one Markdown table row per test case.

## Metadata

- Feature:
- Scope / CR:
- Version or environment:
- Preconditions shared by all cases:
- Owner / last reviewed:

## Test cases (illustrative only)

**The rows below demonstrate the Markdown format. They are not verified test cases and do not imply that Category screens or APIs are implemented in this monorepo.** Replace them with scenarios backed by approved requirements and actual behavior before promoting a suite.

| ID | Scenario | Preconditions | Steps | Test data | Expected result | Priority | Type |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EX-001 | Create an example item | Example administrator is signed in | 1. Open example list; 2. Select Create; 3. Enter data; 4. Save | Name: Sample | Example item is displayed | P1 | Positive |
| EX-002 | Reject invalid example data | Example form is available | 1. Open example form; 2. Enter invalid data; 3. Save | Name: empty | Validation message is displayed | P1 | Negative |

Keep table cells on one line; separate numbered steps with semicolons for portable Markdown.

## Conventions

- IDs are stable, feature-prefixed and unique (e.g. `CAT-001`).
- Each row is executable independently; write shared setup in metadata and case-specific setup in Preconditions.
- Include observable expected outcomes, not vague phrases like "works correctly".
- Cover happy path, validation, permissions, boundaries and failures proportionally.
- Priority: P0 critical, P1 high, P2 normal, P3 low. Type: Positive, Negative, Boundary, Security, Integration or Regression.
- Do not include actual secrets or customer personal data.
- This is a **test specification**, not execution results. Track Pass/Fail, tester and run date separately when running tests.
