# Agent instructions — Ecommerce monorepo

Read [docs/README.md](docs/README.md) and [bootstrap guide](docs/06-development/bootstrap.md) before changing structure.

- Bootstrap stage: do not copy implementation from legacy repositories without an explicit migration CR.
- Prefer small, reviewable changes: one CR/issue, one feature branch, plan → implement → test → review → PR.
- Keep `backend/` and `web/` applications independently buildable; do not create shared dependencies without a concrete use case.
- Domain migration is incremental, with explicit boundaries, tests, contracts and documentation impact.
- Never assume a planned service, integration, workflow or deployment already works.
- API/event contracts must be validated when changed. Do not duplicate generated OpenAPI as hand-written source of truth.
- Do not introduce AI/MCP tools or skills in this bootstrap change; evaluate separately.
