# Documentation index

Durable project documentation lives in `docs/`. Temporary CR plans, reviews and walkthroughs belong in ignored `.agent-work/`.

| Area | Purpose | Entry point |
| --- | --- | --- |
| `01-product/` | Roadmap and milestones | [Roadmap](01-product/roadmap.md), [M1 phases](01-product/milestones/m1-phases.md) |
| `02-domain/` | Domain models and business rules | Reserved |
| `03-architecture/` | Structure, ADRs, structural diagrams | [Architecture](03-architecture/README.md) |
| `04-contracts/` | Human-readable contract conventions | [Contract docs](04-contracts/README.md) |
| `05-flows/` | Business and cross-service sequences | [Flows](05-flows/README.md) |
| `06-development/` | Developer setup and workflow | [Development index](06-development/README.md) |
| `07-operations/` | Operational runbooks | Reserved; [local infrastructure](../infra/README.md) |
| `08-design/` | UI/UX and design system | Reserved |
| `09-quality/` | QA specifications | [Manual QA](09-quality/manual/README.md) |
| `10-learning/` | Technical learning notes | [Docker networking](10-learning/001-docker-networking-fundamentals.md) |

Additional guides: [Conventions](06-development/conventions.md), [Agent instructions](../AGENTS.md), [Code intelligence](../tooling/code-intelligence.md), [Backoffice](../web/backoffice/README.md), [Windows Maven runner](../tooling/scripts/README.md).

Machine-readable API and event schemas belong in [root `contracts/`](../contracts/README.md), not `docs/04-contracts/`. Distinguish proposals from implemented behavior.
