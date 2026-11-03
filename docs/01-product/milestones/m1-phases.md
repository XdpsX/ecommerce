# M1 — Single-Shop Backoffice: Phase Plan

> **Status:** Proposed — for review, not yet approved.
> **Milestone:** M1 — Single-Shop Backoffice  
> **Source of truth:** [Product roadmap](../roadmap.md), [Architecture direction](../../03-architecture/README.md), [Development workflow](../../06-development/workflow.md).
>
> This document proposes delivery phases within M1. It does **not** assert that these capabilities have been implemented, that the target service topology is finalized, or that phase order is a committed schedule.

## 1. Milestone outcome

Deliver the backend capabilities and an integrated **Backoffice that can operate one shop**. Each delivered capability should include the necessary business rules, APIs/contracts, authorization, tests, and usable admin UI. M1 is **not** complete merely because APIs or screens exist independently.

The current monorepo contains Spring Boot skeletons for `ecommerce`, `identity`, `media`, and `notification`, a React Backoffice skeleton, and placeholders for Storefront, BFFs and contracts; PostgreSQL/Redis infrastructure is configured through Compose. The architecture direction is an Ecommerce **modular monolith** with separate Identity, Media, and Notification capabilities; Backoffice BFF/API Gateway is approved by [ADR-0001](../../03-architecture/adr/0001-backoffice-bff-as-api-gateway.md), but not implemented; Storefront BFF is deferred.

### Guiding principles

- **Single-shop first:** one shop, admin-operated; seller tenancy, commissions, and marketplace ownership boundaries are deferred to M3.
- **Capability-driven, not code-porting:** use legacy code and UI to discover scenarios and rules, then re-evaluate them for the new system.
- **Vertical delivery:** backend, authorization, integration, tests, and Backoffice UI should form a verifiable outcome per phase.
- **No premature architecture commitments:** establish service ownership, authentication flow, contracts, and data boundaries through explicit decisions before cross-service implementation.
- **Incremental planning:** phase descriptions are planning buckets; refine only near-term CRs. One focused issue/CR normally maps to one PR, following the repository workflow.
- **Storefront belongs to M2:** M1 may need backend flows to create/test orders, but does not require customer-facing Storefront screens.

## 2. Proposed phase overview

| Phase | Name | Proposed branch | Intended outcome | Primary areas |
| --- | --- | --- | --- | --- |
| **P1** | Core Services & Backoffice Foundation | `phase/1-core-services-foundation` | An admin can authenticate and use a connected Backoffice shell; foundational services and local integration are ready | Identity, Media, Ecommerce, Backoffice, infrastructure; Notification boundary |
| **P2** | Catalog Management | `phase/2-catalog-management` | Admin can manage categories, brands, and variant options/values | Ecommerce, Media, Backoffice |
| **P3** | Product, SKU & Pricing Management | `phase/3-product-sku-pricing` | Admin can create, edit, publish, and price products and SKUs, including media | Ecommerce, Media, Backoffice |
| **P4** | Inventory Management | `phase/4-inventory-management` | Admin can view and adjust SKU stock with consistent inventory rules | Ecommerce, Backoffice |
| **P5** | Order & Fulfillment Management | `phase/5-order-fulfillment` | Admin can inspect and process real orders through a defined lifecycle | Ecommerce, Identity, potentially Notification, Backoffice |
| **P6** | Refund & After-Sales Operations | `phase/6-refund-after-sales` | Admin can process supported cancellation/refund cases and their state transitions | Ecommerce, potentially Notification, Backoffice |
| **P7** | Backoffice Completion & End-to-End Validation | `phase/7-backoffice-completion` | The supported single-shop admin journeys work together reliably | All affected components |

Branch names are **proposed conventions**, not existing Git branches. They can be adjusted when phase boundaries are approved.

The phase sequence is a **proposed dependency order**, not a commitment that all work must happen serially. Cross-cutting testing, accessibility, observability, security, and documentation are required throughout, not deferred to P7.

## 3. Phase details

### P1 — Core Services & Backoffice Foundation

**Goal:** establish the working platform and integrations required to build business capabilities safely.

**Candidate scope**

- **Identity:** choose and implement the minimum viable admin authentication/session and authorization flow; protect admin APIs; define the admin bootstrap process and identity ownership. Evaluate `XdpsX/practical-identity` as a reference, not a migration mandate.
- **Media:** establish upload/storage, metadata, access/security rules, and a basic media lifecycle/API usable by later Catalog and Product workflows. Decide who owns attachment relationships and cleanup responsibilities; avoid prematurely implementing every resource-specific rule.
- **Ecommerce:** set module conventions, persistence/migration approach, error/validation conventions, security integration, and the initial API contract strategy.
- **Backoffice:** build layout/navigation, login/logout, protected routes, API client and error handling, foundational UI components, and a basic admin landing page.
- **Infrastructure/integration:** provide a reproducible local environment and service configuration; decide cross-service communication, contracts, and data ownership for the initial flows.
- **Notification:** clarify intended ownership and triggering mechanism; implement only a justified minimum use case, if one exists. A complete Notification service is **not** an automatic P1 requirement.

**Exit criteria**

- A newly bootstrapped environment can run the relevant applications and reproduce the admin login flow.
- An authenticated admin can reach Backoffice; unauthorized access is rejected by the backend, not only hidden by UI routing.
- The foundational Media API can be exercised with authorization and error handling; resource-specific attachment rules may remain for P2/P3.
- Service ownership and contracts used by P1 are documented; relevant tests and CI checks pass.

**Open decisions:** identity/session model, authorization roles/claims, Media storage and lifecycle, service-to-service trust, database ownership, Backoffice BFF integration details (ADR-0001 approved the decision), and the precise Notification minimum.

### P2 — Catalog Management

**Goal:** enable admin maintenance of the catalog structure that Product depends on.

**Candidate scope**

- Category hierarchy, parent/child rules, ordering/movement, visibility/status, slug behavior, and safe deletion rules.
- Brand creation/editing, status, image, and category association where justified.
- Variant option/value definitions, uniqueness, ordering, and status rules.
- Admin list/detail/edit flows, validation, empty/loading/error states, and concurrency handling for conflicting edits.
- Integrate Media for category/brand images as needed.

**Exit criteria:** an admin can maintain a valid catalog structure from Backoffice; invalid hierarchy/uniqueness operations are rejected consistently; persistence, API, authorization, and UI tests cover critical flows.

**Legacy references:** `ecommerce-be` catalog modules; `ecommerce-backoffice` Categories, Category Structure, Brands, and Variant Options screens. Their existing constraints and contracts are inputs for review, **not approved new-system rules**.

### P3 — Product, SKU & Pricing Management

**Goal:** make products and purchasable SKU definitions manageable through Backoffice.

**Candidate scope**

- Product metadata, category/brand assignment, slug and publication rules.
- SKU/variant identity, option combinations, validation, activation/status, and price/sale-price behavior.
- Product gallery/media attachment, ordering, replacement, and cleanup lifecycle.
- Product list/search, detail, creation/editing, publication actions, and SKU/pricing editor UI.

**Exit criteria:** an admin can create and publish a valid product with SKUs and media, edit it without violating uniqueness/price rules, and inspect it through Backoffice. Critical transitions and Media integration have automated coverage.

**Open decisions:** product-versus-SKU publication semantics, currency/price model, editability of SKU identity, and ownership of attached Media.

### P4 — Inventory Management

**Goal:** support reliable stock operations at SKU level.

**Candidate scope**

- Stock balance and availability semantics; adjustment operations with reason and audit information.
- Concurrency protection, non-negative/oversell rules, and authorization.
- Backoffice stock view and adjustment UI, connected to Product/SKU detail where appropriate.
- Define the inventory reservation contract required by later checkout/order flows.

**Exit criteria:** authorized admins can inspect and adjust stock; invalid or concurrent operations cannot silently corrupt balances; auditability and automated tests cover critical cases.

**Open decisions:** reservation timing, release semantics, inventory ledger/history exposure, and whether separate inventory pages are necessary.

### P5 — Order & Fulfillment Management

**Goal:** make order operations usable in Backoffice with real, testable backend state transitions.

**Candidate scope**

- Order lifecycle, item/address/price snapshots, admin list/search/detail, and supported fulfillment actions.
- Define the **minimum backend-only order creation path** needed for M1 verification; this may require checkout, payment state, and inventory reservation capabilities without implementing Storefront UI.
- Payment state and verification/idempotency rules where required by the selected M1 order journey.
- Authorization, consistency across inventory/payment/order transitions, and optional Notification triggers when justified.

**Exit criteria:** test or API flows can produce representative orders; admin can find, inspect, and advance them only through allowed transitions; integration tests cover stock/payment/order consistency and failures.

**Open decisions:** M1 payment method/simulator, checkout depth versus M2, order cancellation boundary, fulfillment model, and event/notification contracts. **Do not assume legacy VNPay integration is mandatory.**

### P6 — Refund & After-Sales Operations

**Goal:** enable the agreed after-sales operations for a single shop.

**Candidate scope**

- Supported cancellation and refund requests, state transitions, idempotency, and manual/automated handling boundaries.
- Refund inbox/detail and actionable admin workflows, with audit and authorization.
- Payment/order/inventory reconciliation for supported cases; failure/retry handling.
- Notification integration only for approved use cases.

**Exit criteria:** admin can process representative supported cancellation/refund cases end-to-end, including retry/failure paths, without invalid financial or order state transitions.

**Open decisions:** whether refunds are manual in M1, refund provider integration, restocking rules, and post-shipment return scope.

### P7 — Backoffice Completion & End-to-End Validation

**Goal:** validate that M1 delivers a coherent single-shop operating tool, not a collection of disconnected demos.

**Candidate scope**

- Close remaining gaps across navigation, forms, search/filtering, empty/error/loading states, responsive behavior, accessibility, and usability.
- End-to-end journeys across authentication → catalog → product/SKU → inventory → order → refund.
- Review authorization boundaries, API contracts, operational setup, observability, migration/recovery concerns, and documentation.
- A practical admin landing/dashboard; **advanced analytics and KPI APIs are not implied**.

**Exit criteria:** agreed M1 acceptance journeys pass against integrated services with representative data; major security/data-integrity risks are addressed; deployment/developer instructions and known limitations are documented.

**Note:** P7 is a final integration/quality gate, **not** permission to postpone tests or UX quality from P1–P6.

## 4. Dependencies and cross-service boundaries

- **P1 → P2/P3:** Identity and foundational Media integration are prerequisites for protected catalog/product management.
- **P2 → P3:** category, brand, and variant definitions underpin product and SKU creation.
- **P3 → P4:** inventory is tracked against defined SKUs.
- **P4 → P5:** order creation/reservation must respect stock consistency; some order-domain design can begin earlier.
- **P5 → P6:** refund/cancellation semantics depend on defined order/payment state models.
- **P1–P6 → P7:** the completion gate integrates the outcomes of earlier phases.

For every cross-service CR, identify **producer, consumer, contract, data owner, and compatibility/rollout**. Do not introduce a BFF, shared database, event broker, or synchronous call chain solely because it appears in a reference project.

## 5. Working scope boundaries

**In M1 (subject to phase-level approval):** admin authentication/authorization; catalog, products/SKUs/pricing, inventory, orders/fulfillment, supported cancellation/refunds; Media and other service integrations necessary to operate the Backoffice; tests and developer/operational readiness.

**Out of M1 by default:** customer-facing Storefront screens (M2), multi-shop/seller/marketplace functionality (M3), multi-warehouse logistics, full promotion engine, advanced BI/analytics, and integrations without an approved single-shop use case. These exclusions can be revisited explicitly, not assumed silently.

## 6. Reference inventory and status caveats

| Source | How to use it |
| --- | --- |
| [Current monorepo](https://github.com/XdpsX/ecommerce) | Architectural direction, conventions, active implementation status, and durable decisions |
| [Legacy Ecommerce BE](https://github.com/XdpsX/ecommerce-be) | Business cases and implementation trade-offs across auth, catalog, product, inventory, order, payment, refund, media |
| [Legacy Backoffice](https://github.com/XdpsX/ecommerce-backoffice) | UX patterns and existing admin screens for login, categories, brands, and variant options |
| [Identity reference](https://github.com/XdpsX/practical-identity) | Optional Identity architecture/implementation ideas |
| [Notification reference](https://github.com/XdpsX/notification-service) | Optional Notification architecture/implementation ideas |

Existing legacy screen inventories and API handoff notes may inform acceptance criteria, but their API paths, constraints, providers, and screen counts are **not** automatically binding on the new monorepo.

## 7. Git branch strategy

Follow the canonical [development workflow](../../06-development/workflow.md): create authorized phase integration branches from `develop`, target CR branches to their phase branch, and merge completed phases back into `develop` through reviewed PRs. Do not add a milestone integration branch by default. Branch names in the phase overview remain proposals, not existing branches.

## 8. Planning and tracking

- **Milestone:** M1 tracks the outcome; this file records proposed phase boundaries, not live progress.
- **Phase:** use GitHub Project fields/views for delivery stages, per [workflow](../../06-development/workflow.md).
- **CR:** one GitHub issue normally represents one focused change, with acceptance criteria, dependencies, affected applications, and verification. Refine near-term CRs first.
- **Decisions:** settle material architecture/domain choices before implementing dependent CRs; capture durable approved decisions in the appropriate docs/ADRs.
- **Status:** GitHub Issues/Projects/PRs remain the source of truth for execution status; do not maintain duplicate progress checklists here.

## 9. Decisions needed before approving this plan

1. Confirm whether the proposed seven phases and their boundaries fit the intended M1 scope.
2. Confirm P1's **minimum usable Identity and Media capabilities** and whether Notification has a concrete P1 use case.
3. Agree on M1's minimum order-creation/payment scenario without building the Storefront.
4. Decide the supported fulfillment, cancellation, and refund depth for M1.
5. Resolve service ownership/contracts and the local integrated runtime approach before cross-service CRs.

Until these are resolved, treat the phase descriptions as a **working proposal** rather than a frozen delivery commitment.
