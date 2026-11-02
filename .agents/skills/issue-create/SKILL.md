---
name: issue-create
description: Draft or, with explicit authorization, create a GitHub change-request issue using the repository issue template. Use when capturing an approved CR in the backlog; do not implement the change.
---

# Create a change-request issue

Read root `AGENTS.md`, `docs/06-development/workflow.md`, and `.github/ISSUE_TEMPLATE/change-request.yml`. Use the current template as the source of truth; do not replace it with a generic issue format.

1. Confirm repository, problem/context, observable goal, affected applications, testable acceptance criteria, target integration branch and out-of-scope work. Identify dependencies, risks and verification approach where relevant. Ask about blocking unknowns; do not invent business rules, branch names or acceptance criteria.
2. Search existing open issues for duplicates or overlapping scope before creating a new one. Suggest updating an existing issue when appropriate.
3. Draft a concise title (retain the template's `[CR]` convention) and Markdown body with the template's field labels in order: **Problem / Context**, **Goal / Expected outcome**, **Scope / Affected areas**, **Acceptance criteria**, **Out of scope**, **Target integration branch**, **Dependencies / Risks / Open questions**, **Verification approach**. Use checkboxes for acceptance criteria; use N/A only where genuinely applicable.
4. Propose one existing `type:*` label, one or more existing `area:*` labels, and at most one existing `priority:*` label. Verify labels exist rather than silently creating them. Assign the correct existing GitHub Milestone when known; GitHub Project status is managed separately. Never invent milestone IDs, assignees or labels.
5. Present the draft and metadata for review when creation is not explicitly authorized. Create the GitHub Issue only on explicit user instruction; if the request already authorizes creation, proceed after resolving blockers. Verify the created issue and return its URL, number, milestone and applied labels. Report any Project assignment that could not be performed.

## Boundaries

- Issue creation does not grant permission to create/switch branches, commit, push, implement or open PRs.
- Keep one coherent CR per issue; avoid splitting DTO/mapper/test subtasks into standalone issues unless independently deliverable.
- Follow the repository's reference-only policy: legacy code and external repositories are inputs for analysis, not mandatory implementation designs.
- If the GitHub integration cannot create an issue or set metadata, return the prepared content and state the limitation; never claim success without confirmation.
