# Change request workflow

One issue normally represents one change request (CR). Small, low-risk bootstrap setup may proceed directly on the integration branch when explicitly authorized.

1. **Scope:** Record problem, intended outcome, acceptance criteria, affected applications and out-of-scope work. Identify the milestone integration branch.
2. **Branch:** For ordinary CRs, create a task branch from the milestone integration branch and target that branch with the PR. Never create or switch branches without authorization.
3. **Plan (`$cr-plan`):** Inspect existing behavior, ownership, interfaces, dependencies, migration and compatibility risks. Resolve decisions that materially affect behavior before implementation. Planning is read-only.
4. **Implement (`$cr-implement`):** Deliver the smallest coherent approved slice; add proportional tests, required migrations and contract updates. Use the affected application's tooling and check the final diff.
5. **Review (`$cr-review`):** Review the complete change against its intended base and acceptance criteria. Prioritize correctness, security, data integrity, compatibility, boundaries, tests and documentation drift. Review is read-only.
6. **PR:** Reference the issue when present, report verification and risks, and merge after required checks and human review. Only create commits or PRs when requested.

For cross-service work, name the producer, consumer, contract, data owner and rollout/compatibility considerations. Keep ephemeral plans in the issue or PR; write durable documentation only when useful beyond the CR.

## Agent artifacts

See `agent-artifacts.md`. Nontrivial CR plans: `.agent-work/plans/<cr-id>.md`; full reviews: `.agent-work/reviews/<cr-id>.md`; implementation code-reading walkthroughs: `.agent-work/implementations/<cr-id>.md`; QA drafts: `.agent-work/test-cases/<feature>.md`. These are local and ignored. Only reviewed reusable manual test suites belong in `docs/qa/manual/` and are committed after approval.
