# Change request workflow

1. Create a GitHub issue describing problem, scope, acceptance criteria and affected applications.
2. Branch from the current milestone integration branch; do not work directly on the default branch.
3. Plan: inspect source, contracts, dependency impact, risks and tests.
4. Implement only the agreed slice, with tests and migrations as needed.
5. Review correctness, security, backward compatibility, module boundaries and docs drift.
6. Open a PR referencing the issue; merge only after required checks and human review.

For cross-service work, explicitly name producers, consumers, contracts and ownership.
