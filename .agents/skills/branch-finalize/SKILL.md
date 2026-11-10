---
name: branch-finalize
description: Prepare a final task branch from a reviewed draft branch, squash its changes and create coherent grouped commits. Always present a concrete proposal and wait for approval before Git mutations; excludes push, PR creation and merging.
---

# Finalize a draft branch

Read root `AGENTS.md`, `docs/06-development/conventions.md`, `docs/06-development/workflow.md` and affected application READMEs. This workflow changes Git history organization, not application behavior. Preserve the draft branch and unrelated work.

## 1. Inspect and propose (no Git mutations)

Invoking this skill requests a proposal, not execution. Even “finalize this branch” starts here. Present the proposal and end the turn awaiting explicit approval; do not create/switch branches, create worktrees, stash, stage, commit or alter files first. An ignored proposal under `.agent-work/plans/` is allowed.

- Identify the issue/approved scope, draft branch and HEAD, intended integration branch and its SHA, proposed final branch name and whether that name already exists locally/remotely. Verify the base from issue/workflow evidence; do not assume `develop` or the draft's upstream is the integration base.
- Inspect the complete draft-versus-base diff, merge base, ahead/behind counts and working tree, including staged, unstaged and relevant untracked files. Record their statuses and content fingerprints so approval binds to an identifiable snapshot. Do not print secrets. Report remote freshness; use read-only remote lookup when available. Do not fetch or synchronize implicitly.
- Read the latest review and verification evidence. Distinguish passed checks, stale evidence, unresolved findings and user-approved exclusions. This skill does not replace a code review or silently resolve findings.
- Inventory final files individually. Classify include, exclude or unresolved, with reasons. Review documentation by content and purpose: retain relevant durable docs, README, contracts and approved ADRs; exclude temporary plans/reviews/spikes, generated build output, credentials and unrelated work. Do not blanket-include or blanket-delete `docs/` or Markdown files. A tracked temporary file already in the base requires an explicit proposed deletion, not just omission.
- For each changed documentation file, read its actual diff and enough surrounding content to identify its audience, canonical owner and relevance to the approved change. Show a separate documentation decision table: path, purpose of the changed content, include/exclude/partial/unresolved and specific reason. A durable filename, README location or presence in draft history is not sufficient justification. Exclude redundant narratives, superseded proposals, personal notes and unrelated doc edits; distinguish these from necessary contract, security, operations, setup and architecture updates. Do not omit documentation required to explain the shipped behavior. For mixed-purpose files, propose exact included/excluded hunks and check retained links; preserve excluded content on the draft. Unchanged base documents are preserved, not recommitted. Do not publish new ADRs or rewrite accepted decisions without their own authorization.
- Propose ordered commits with exact file lists (including renames/deletions), messages, purpose and dependencies. Prefer coherent feature/configuration/test/contract slices rather than splitting only by extension; keep each commit buildable where practical. Group cross-cutting organization changes separately when needed. If one file must span commits, show the hunk split explicitly.
- Present the complete literal commit message for every group, in English: a concrete Conventional Commit subject, an optional useful body and a blank line followed by `Refs: #<issue-number>`. Use the verified related issue, never a placeholder in the reviewable proposal or a guessed number. If no issue applies, disclose the missing reference and agree the exception before execution. Do not substitute `Closes`/`Fixes` for `Refs` or imply issue closure. Commit exactly the approved messages; adding a reference alone does not justify unrelated files. Use structured tool arguments or UTF-8 message files with `git commit --file` to preserve newlines.
- Propose the transfer method and checks. Normally create a fresh final branch from the verified integration SHA, apply the draft using `git merge --squash` without a merge commit, then commit approved groups. This produces several new commits without copying the draft's commit history.
- For a dirty draft, propose an isolated worktree plus a captured patch/file snapshot for approved uncommitted changes; preserve the original index, working tree and untracked files. Do not require a temporary draft commit or stash without approval. Transfer committed changes first, then apply the approved uncommitted delta; account for included untracked files explicitly.
- If the base diverged, disclose base-only changes, possible conflicts and how final comparison will account for them. Do not rebase/merge the integration branch into the draft. Name any necessary conflict resolution in the proposal; material behavior decisions require renewed approval.

### Proposal format

1. **Branches and snapshot:** draft/head, target/SHA, merge base, divergence, proposed final branch and dirty-state inventory.
2. **Files:** include/exclude/partial/unresolved tables with paths and reasons; link an ignored complete manifest if long. Include the separate documentation decision table based on actual content.
3. **Commits:** complete English messages including `Refs: #<issue-number>`, exact files/hunks and dependencies, in execution order.
4. **Execution and verification:** worktree/transfer method, expected differences from draft, checks to run and known limitations.
5. **Approval request:** ask whether to execute this specific proposal, stating that this skill requires approval before Git mutations. Push, PR and merge are outside this request.

Do not hide unresolved files behind an automatic default. Resolve them with the user before execution. A short “OK” approving this presented proposal is sufficient; do not demand a special phrase or ask again for each approved commit.

## 2. Execute only the approved proposal

- Recheck the recorded snapshot before the first mutation. If source/base contents or commit grouping materially changed, present the delta and obtain approval for the changed proposal. Existing approval remains valid for unchanged steps.
- Create the approved final branch from the approved base, preferably in a separate worktree when the draft is dirty. Fail safely if the branch/worktree already exists; never overwrite it or move an existing branch pointer automatically.
- Perform the approved squash transfer and uncommitted-file transfer. Do not cherry-pick the original commit series. Never use a broad destructive reset/clean, force checkout, amend/rebase of existing history, or automatic conflict strategy to make the transfer succeed.
- Withhold excluded draft additions/edits from the final result while preserving their original copies. Preserve target-only changes. Do not delete approved target content merely to achieve a byte-identical draft tree.
- Stage exact paths/hunks for each approved group. Inspect the staged diff, filenames and `git diff --cached --check` before each commit; avoid `git add .`, `git add -A` or directory-wide staging that could capture unapproved files. Verify no temporary artifacts or secrets are included.
- Run checks suited to the changed applications and transfer risk. Reuse previous evidence only with a clear statement of what snapshot it covered; run full required checks when base changes or conflict resolutions invalidate it. Do not silently run source-mutating formatters or fix unrelated defects.
- Commit the approved groups with the proposed messages. Stop on unexpected conflicts, failing checks or ambiguous content; preserve progress and report exact state. Do not roll back completed commits destructively. On resumption, inspect existing commits and continue without duplicating them.

## 3. Verify and hand off

- Compare the final tree to the approved source snapshot. With an unchanged base this should match apart from explicitly excluded files. With a newer/diverged base, reconcile every additional difference against base-only changes and reviewed conflict resolutions. Compare content and file modes, not only commit counts; include approved dirty/untracked content in the comparison.
- Verify the final branch descends from the approved base and contains only the new planned commits above it, with no draft-history or merge commits. Report renames, exclusions and any deliberate deviations.
- Confirm the original draft HEAD, index, working tree and untracked content remain intact. Report any tool-generated artifacts separately. Do not automatically delete the draft branch, worktree or temporary evidence.
- Report final branch/base, each commit SHA/message, file/doc exclusions, checks run/skipped and remaining limitations. State local versus remote status accurately.
- Stop here. No push, force push, PR, merge, issue closure or branch deletion is authorized by this workflow. Use `$pr-create` only when separately requested.
