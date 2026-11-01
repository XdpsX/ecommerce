---
name: architecture-review
description: Evaluate monorepo architecture decisions and trade-offs before implementation, including service ownership, shared libraries, synchronous/asynchronous integration and compatibility. Read-only.
---

# Review architecture options

Read root `AGENTS.md`, `docs/06-development/workflow.md`, `docs/06-development/conventions.md` and relevant application documentation.

1. State the decision, constraints, current architecture and evidence. Identify what is unknown.
2. Determine which application owns each business capability and its data; map producers, consumers and contracts.
3. Compare the simplest viable alternatives, including keeping behavior within an existing service. Consider coupling, operational complexity, transactions, failure handling, security, migrations and backward compatibility.
4. Recommend one option with concrete trade-offs, boundaries and incremental rollout steps. Distinguish current requirements from speculative future needs.
5. Identify validation experiments or questions that must be answered before coding.

## Output

Provide the decision and recommendation first, then alternatives, trade-offs, affected services/contracts, risks and unresolved questions. Do not edit files, branches or PRs unless separately authorized.

## Optional code intelligence

Follow `docs/06-development/code-intelligence.md`; GitNexus and Serena are optional. Verify graph results against source, use direct source inspection when unavailable, and do not install or index automatically.

## Artifacts

Recommend inline by default. Only write a durable ADR when approved; see `docs/06-development/agent-artifacts.md`.
