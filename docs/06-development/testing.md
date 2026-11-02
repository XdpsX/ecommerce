# Testing strategy (v1 — Category pilot)

This document is the single source of truth for test selection, conventions, TDD and snapshot policy across the Ecommerce monorepo. Apply the policy proportionately; do not create tests solely to satisfy a quota. The first evaluation is the Category Management feature across backend and Backoffice.

## Test boundaries

| Area | Test type | Purpose | Default tooling |
| --- | --- | --- | --- |
| Backend | Unit (UT) | Isolated domain rules and meaningful application logic | JUnit, Mockito where needed |
| Backend | Integration (IT) | HTTP/API, persistence, security, transactions and important infrastructure boundaries | Spring Boot Test, Testcontainers when needed |
| Frontend | Unit (UT) | Pure functions, transformations, validation helpers and nontrivial hooks | Vitest |
| Frontend | Component | Rendered behavior, accessibility and user interaction (forms, tables, actions) | Vitest, React Testing Library, user-event |
| Frontend | Integration (IT) | Feature/page behavior across components, router, state and HTTP boundary | Vitest, React Testing Library, MSW |
| E2E | End-to-end | Critical business journeys against a running integrated system | Playwright in the separate E2E repository |

Frontend component and integration tests may use the same runner and `.test.ts(x)` suffix; they differ by scope, not tooling. MSW mocks HTTP boundaries for FE IT: do not require a live backend for ordinary frontend CI. Backend IT should use real infrastructure where the boundary under test matters; avoid mocking the very boundary being verified.

## Selection and coverage

- Start from the CR acceptance criteria and risk. Cover representative successful behavior, meaningful failures and boundaries where relevant; do not force every category for trivial changes.
- Prefer testing observable behavior over implementation details. Do not test private methods, React internal state or styling-only markup merely for coverage.
- Prioritize business invariants, permissions, data integrity, validation, error handling, API contracts and user interactions.
- Avoid redundant assertions across UT, IT and E2E: each layer should establish distinct confidence.
- No repository-wide coverage percentage gate during the pilot. Coverage can inform review but does not replace meaningful assertions.
- Test new/changed important behavior; do not require exhaustive retroactive coverage of untouched bootstrap code.

## Naming and placement

- Backend: keep tests under `src/test` in the corresponding application; prefer `*Test.java` for unit tests and `*IT.java` for integration tests. Ensure Surefire/Failsafe and CI actually discover the chosen patterns before relying on them.
- Frontend: colocate `*.test.ts` / `*.test.tsx` with source; keep shared test setup, MSW handlers and render helpers under `src/test/`. Prefer role-based queries and user-event.
- E2E scenarios and screenshot baselines live in the separate E2E repository, not duplicated in frontend CI.

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

## Category Management pilot and review

- Backend: exercise hierarchy/invariants with UT; persistence, API and authorization with targeted IT.
- Backoffice: test tree helpers if they contain logic; CategoryForm interaction/validation with component tests; category list loading, errors and mutations with an MSW-backed feature IT.
- Separate E2E repository: one critical journey (admin creates a category and verifies it appears).
- After the pilot, assess bugs caught, refactoring confidence, execution speed, flakiness, maintenance cost and TDD usefulness. Adjust this document before making stronger repository-wide requirements.
