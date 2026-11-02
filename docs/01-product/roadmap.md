# Product roadmap

This document describes the agreed product direction, not live implementation status. GitHub Milestones and Projects track delivery progress. The monorepo is currently in M0 bootstrap; application skeletons are not evidence of completed business features.

| Milestone | Target outcome |
| --- | --- |
| M0 — Platform Foundation & Repository Bootstrap | Establish monorepo, CI, developer workflow, testing and documentation foundations |
| M1 — Single-Shop Backoffice | Complete the backend capabilities and Backoffice needed to operate **one shop**, using existing reference modules as a scope starting point |
| M2 — Single-Shop Storefront | Complete the customer-facing Storefront and supported shopping journeys for **one shop**, building on M1 capabilities |
| M3 — Multi-Shop / Marketplace | Evolve the single-shop system to support multiple shops, including appropriate ownership, authorization and data boundaries |
| M4+ | Not defined yet; decide after learning from earlier milestones |

## Scope and delivery principles

- **Single-shop first:** M1 and M2 target one shop; multi-shop behavior belongs to M3. Avoid premature marketplace complexity while keeping reasonable extension points.
- **Capability-driven, not code-porting:** inventory existing modules in reference repositories to identify business capabilities. Reassess requirements and implementation; do not assume old code, APIs, database models or UI are authoritative.
- **Backend + UI as a working outcome:** M1 includes backend business rules, API, authorization, tests and Backoffice screens—not just frontend pages. M2 adds the complete supported customer-facing flows.
- **Design improvements encouraged:** Backoffice and Storefront may use new UX/UI rather than reproducing legacy screens; Impeccable may be used in the design workflow.
- **Incremental planning:** audit current and reference implementations before dividing M1 into phases and actionable CRs. Refine near-term work first; do not commit to a module-by-module schedule prematurely.

## Reference repositories

Legacy Ecommerce backend and Backoffice are a starting point for capability discovery, not a mandatory implementation specification. Identity, Notification and external examples provide optional architectural and engineering ideas. Evaluate each pattern for this project's needs rather than copying it by default. See the repository README for the reference links.
