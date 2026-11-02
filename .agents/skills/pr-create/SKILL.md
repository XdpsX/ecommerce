---
name: pr-create
description: Draft or, with explicit authorization, open a GitHub pull request following the repository PR template after implementation and review. Use for CR handoff, not for implementation or merging.
---

# Create a change-request pull request

Read root `AGENTS.md`, `docs/06-development/workflow.md`, `.github/PULL_REQUEST_TEMPLATE.md`, and the related issue/CR. The current PR template is authoritative.

1. Identify the source task branch, intended integration base (typically `phase/**` for a milestone CR), linked issue and approved acceptance criteria. Verify branch names, branch head and comparison base rather than guessing. Do not create/switch branches, commit or push without separate authorization.
2. Inspect the complete source-versus-base diff, changed applications/contracts, existing PRs and any review findings. Stop or clearly disclose blocking findings and uncommitted/unpushed work; do not claim changes are in GitHub when they are only local.
3. Draft a clear PR title and populate every section in `.github/PULL_REQUEST_TEMPLATE.md` in order: **Summary**, **Related issue / CR**, **Scope and changes**, **Verification**, **Risks and compatibility**, **Review checklist**, **Reviewer notes**.
4. Report exact commands and observed results, distinguish successful, failed and not-run checks, and check a template box only when supported by evidence. Never infer that CI, tests or review passed merely because the workflow exists. Mention migrations, compatibility, security, rollout and UI screenshots when relevant.
5. For PRs into milestone integration branches, prefer `Ref #<issue>` to avoid promising automatic closure; use `Closes #<issue>` only when the target and intended lifecycle support closure. Link the issue even if auto-close is inappropriate.
6. Present a draft if opening is not explicitly authorized. If explicitly authorized, create the PR using the verified head/base and template body, then confirm and return its URL, target branch, linked issue and any limitations. Prefer a draft PR if work/review is incomplete and the user authorized opening one; otherwise ask before publishing a ready-for-review PR.

## Boundaries

- Do not merge, approve, close issues, change branch protection or fabricate checks.
- Do not substitute this skill for `$cr-review`; report review findings and unresolved risks honestly.
- If GitHub integration cannot create a PR or the branch is not pushed, provide the complete draft and explain the blocker.
