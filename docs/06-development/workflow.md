# Change request workflow

One issue normally represents one change request (CR). Small, low-risk bootstrap setup may proceed directly on the applicable integration branch when explicitly authorized.

1. **Scope:** Record the problem, intended outcome, acceptance criteria, affected applications and out-of-scope work. Identify the milestone, phase and phase integration branch.
2. **Branch:** For ordinary CRs, create a task branch from the relevant phase integration branch and target that same phase branch with the PR. Never create or switch branches without authorization.
3. **Plan (`$cr-plan`):** Inspect existing behavior, ownership, interfaces, dependencies, migration and compatibility risks. Resolve decisions that materially affect behavior before implementation. Planning is read-only. Identify any durable docs affected by the proposed change.
4. **Implement (`$cr-implement`):** Deliver the smallest coherent approved slice; add proportional tests, required migrations and contract updates. Use the affected application's tooling and check the final diff. Use `$docs-update` for approved documentation changes when necessary.
5. **Review (`$cr-review`):** Review the complete change against its intended base and acceptance criteria. Prioritize correctness, security, data integrity, compatibility, boundaries, tests and documentation drift. Use `$docs-audit` for evidence-backed documentation checks when relevant. Review is read-only.
6. **PR:** Reference the issue when present, report verification and risks, and merge after required checks and human review. Only create commits or PRs when requested.

For cross-service work, name the producer, consumer, contract, data owner and rollout/compatibility considerations. Keep ephemeral plans in the issue or PR; write durable documentation only when useful beyond the CR.

## Agent artifacts

See `agent-artifacts.md`. Nontrivial CR plans: `.agent-work/plans/<cr-id>.md`; full reviews: `.agent-work/reviews/<cr-id>.md`; implementation code-reading walkthroughs: `.agent-work/implementations/<cr-id>.md`; QA drafts: `.agent-work/test-cases/<feature>.md`. These are local and ignored. Only reviewed reusable manual test suites belong in `docs/qa/manual/` and are committed after approval.

## Planning and tracking

- **Milestone:** A major product deliverable or outcome (for example, M1 — Single-Shop Backoffice). A milestone contains multiple delivery phases. Assign its CR issues to the corresponding GitHub Milestone.
- **Phase:** A coherent delivery stage within a milestone, with a defined scope, dependencies and completion criteria. Each active phase has its own integration branch named `phase/<number_phase>`. Track phase membership and progress in GitHub Projects; do not create a separate GitHub Milestone for each phase by default.
- **Backlog:** GitHub Issues for proposed work. Keep distant work at capability level; refine the next few CRs with scope, acceptance criteria and dependencies.
- **CR/task:** One issue normally maps to one focused implementation PR, targeting its phase integration branch. Implementation subtasks stay in the issue or plan unless independently deliverable.
- **GitHub Projects:** Canonical live status and prioritization (Backlog → Ready → In Progress → In Review → Done). Issues define scope, PRs record implementation and verification, docs preserve durable decisions. Do not duplicate live status in Markdown.
- **Roadmap:** [Product roadmap](../01-product/roadmap.md) describes milestone outcomes and sequence, not live issue progress. Phase plans, such as `../01-product/m1-phases.md`, document the intended breakdown within a milestone. Link milestone/Project views from the README when available.

## Branches and CI

- **Phase integration branch:** Create `phase/<number_phase>` from `develop` when starting an authorized phase. Use the phase number established by that milestone's phase plan; keep the mapping unambiguous across the repository. Do not introduce a separate milestone integration branch by default.
- **CR branch:** Create a task branch from its phase integration branch and target that phase branch with the CR PR. Keep ordinary CR changes isolated until reviewed and verified.
- **Phase completion:** Once the phase's acceptance criteria are met and its CRs are integrated, open a reviewed PR from `phase/<number_phase>` into `develop`. Promote `develop` to `main` via PR according to release needs.
- **Dependencies and overlapping phases:** Before opening a phase branch or starting a dependent CR, confirm that required changes are available in its base. Coordinate cross-phase dependencies explicitly; do not assume parallel phase branches automatically share changes. Avoid merging unrelated or unfinished phase work merely to resolve a dependency.
- **CI:** Application CI runs on relevant-path PRs (including PRs targeting `phase/**`) and on pushes to `phase/**`, `develop`, and `main`. Workflow dispatch remains available. Path filters may skip checks for unaffected applications. Verify the actual workflow configuration before relying on these triggers.
- Require successful applicable checks and review before merging. Branch protection/rulesets must be configured separately in GitHub settings; workflow triggers alone do not enforce merge restrictions.
- Verify workflow execution and required-check names before enabling branch rules. Avoid requiring path-filtered jobs unconditionally, as skipped workflows can leave required checks pending.
