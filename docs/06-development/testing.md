# Testing strategy

This document is the single source of truth for test selection, conventions, TDD and snapshot policy across the Ecommerce monorepo. Apply the policy proportionately; do not create tests solely to satisfy a quota. Evaluate the policy on real features before adding repository-wide gates.

## Test boundaries

| Area | Test type | Purpose | Default tooling |
| --- | --- | --- | --- |
| Backend | Unit (UT) | Isolated domain rules and meaningful application logic | JUnit, Mockito where needed |
| Backend | Integration (IT) | HTTP/API, persistence, security, transactions and important infrastructure boundaries | Spring Boot Test, Testcontainers for database IT |
| Frontend | Unit (UT) | Pure functions, transformations, validation helpers and nontrivial hooks | Vitest |
| Frontend | Component | Rendered behavior, accessibility and user interaction (forms, tables, actions) | Vitest, React Testing Library, user-event |
| Frontend | Integration (IT) | Feature/page behavior across components, router, state and HTTP boundary | Vitest, React Testing Library, MSW |
| E2E | End-to-end | Critical business journeys against a running integrated system | Playwright in the separate E2E repository |

Frontend component and integration tests may use the same runner and `.test.ts(x)` suffix; they differ by scope, not tooling. MSW mocks HTTP boundaries for FE IT: do not require a live backend for ordinary frontend CI. Backend IT should use real infrastructure where the boundary under test matters; avoid mocking the very boundary being verified.

## Backend database integration tests

- Use Testcontainers with the application's real database engine (e.g. PostgreSQL); run actual schema migrations.
- Default to a static `@Container` per IT class (`@Testcontainers`), shared by its test methods and stopped after the class. Do not start a container per test method.
- Isolate test data: use transactional rollback where valid; otherwise clean/reset data explicitly. Tests must not depend on execution order or another test's data.
- Do not enable reusable containers across test runs by default. Revisit suite-wide sharing only if startup cost warrants it, with explicit lifecycle and isolation safeguards.

## Selection and coverage

- Start from the CR acceptance criteria and risk. Cover representative successful behavior, meaningful failures and boundaries where relevant; do not force every category for trivial changes.
- Prefer testing observable behavior over implementation details. Do not test private methods, React internal state or styling-only markup merely for coverage.
- Prioritize business invariants, permissions, data integrity, validation, error handling, API contracts and user interactions.
- Avoid redundant assertions across UT, IT and E2E: each layer should establish distinct confidence.
- No repository-wide coverage percentage gate during the pilot. Coverage can inform review but does not replace meaningful assertions.
- Test new/changed important behavior; do not require exhaustive retroactive coverage of untouched bootstrap code.

## Naming and placement

- **Backend:** mirror production packages under each service's `src/test/java`. Use `*Test.java` for UT (Surefire) and `*IT.java` for IT (Failsafe); verify Maven/CI discovery. Do not create a test class for every production class—test meaningful behavior.
- **Frontend:** colocate UT, component and feature IT with their source files; use `*.test.ts` for tests without JSX and `*.test.tsx` when JSX is used. Do not split tests into `unit/`, `component/` or `integration/` folders, or require type-specific filename suffixes.
- **Frontend shared fixtures:** reserve `src/test/` for setup, MSW handlers, factories and shared render helpers, not feature test suites. Prefer role-based queries and user-event.
- **E2E:** use `*.spec.ts` in the separate Playwright repository; keep screenshot baselines there too.

Example:

```text
services/ecommerce/src/test/java/.../catalog/category/
  application/CategoryServiceImplTest.java
  api/CategoryControllerIT.java
web/backoffice/src/features/categories/
  components/CategoryForm.tsx
  components/CategoryForm.test.tsx
  utils/categoryTree.ts
  utils/categoryTree.test.ts
  pages/CategoryListPage.tsx
  pages/CategoryListPage.test.tsx
web/backoffice/src/test/
  setup.ts
  mocks/
```

## Snapshot policy

- DOM snapshots are **not** required by default. Use only for a small, stable, reviewable render contract where a snapshot adds value beyond behavioral assertions.
- Visual regression screenshots are **not** a pilot gate. Evaluate Playwright screenshot tests for stable, important screens after the UI settles, in the E2E repository.
- Never auto-update snapshot baselines just to pass CI. Review and approve intentional baseline changes.

## TDD policy

Use TDD selectively, **not** as a universal requirement:

1. Prefer red → green → refactor for new or changed domain invariants and isolated business logic.
2. For bug fixes, first add a regression test that reproduces the bug when practical.
3. TDD is optional for frontend pure logic; component/page and integration tests may be written alongside implementation.
4. Do not force test-first for wiring, exploratory UI, generated code or infrastructure changes where it adds little value.
5. Tests must express agreed expected behavior, not merely mirror a flawed implementation.

## Verification and CI

- Backend PRs: run affected UT and relevant IT; provide database/container prerequisites when required. Do not claim IT ran unless its execution is confirmed.
- Frontend PRs: run relevant Vitest unit/component/integration tests plus applicable lint, format and build checks. FE IT must be deterministic with mocked network boundaries.
- E2E: execute critical journeys in its separate repository, initially on a suitable integration environment or schedule; do not make the entire E2E suite a default gate for every monorepo PR.
- Report exact commands and outcomes, skipped checks and residual risks in the PR. Never mark unexecuted checks as passed.
