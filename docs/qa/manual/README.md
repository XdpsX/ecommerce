# Manual test case format

Store reusable, human-reviewed manual test suites here. Drafts belong in `.agent-work/test-cases/` and are not committed. Use one Markdown table row per test case.

## Metadata

- Feature:
- Scope / CR:
- Version or environment:
- Preconditions shared by all cases:
- Owner / last reviewed:

## Test cases

| ID | Scenario | Preconditions | Steps | Test data | Expected result | Priority | Type |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CAT-001 | Create category with valid data | Admin is signed in | 1. Open Categories; 2. Select Create; 3. Enter valid fields; 4. Save | Name: Accessories | Category is saved and visible in the list | P1 | Positive |
| CAT-002 | Reject duplicate slug | Category with slug accessories exists | 1. Open Create; 2. Enter duplicate slug; 3. Save | Slug: accessories | Request is rejected with an understandable validation message; no duplicate is created | P1 | Negative |

Keep table cells on one line; separate numbered steps with semicolons for portable Markdown.

## Conventions

- IDs are stable, feature-prefixed and unique (e.g. `CAT-001`).
- Each row is executable independently; write shared setup in metadata and case-specific setup in Preconditions.
- Include observable expected outcomes, not vague phrases like "works correctly".
- Cover happy path, validation, permissions, boundaries and failures proportionally.
- Priority: P0 critical, P1 high, P2 normal, P3 low. Type: Positive, Negative, Boundary, Security, Integration or Regression.
- Do not include actual secrets or customer personal data.
- This is a **test specification**, not execution results. Track Pass/Fail, tester and run date separately when running tests.
