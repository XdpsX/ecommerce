# Code organization

This is the default organization for new backend and frontend code and scoped refactors. It defines responsibility and dependency boundaries while allowing each feature to choose an appropriate internal structure. Existing packages may not yet follow it; adoption does not authorize a bulk refactor.

## Approach and scope

Organize backend code by feature or capability first, then by responsibility within that feature. Apply the dependency principles of Clean and Hexagonal Architecture where useful. This convention does not require full DDD, an interface for every class, or a separate Maven module for each feature.

A feature is a cohesive capability within a service, such as administrator management. A package boundary is not a separate deployment or permission to share data across services. Service ownership and approved [architecture decisions](../03-architecture/adr/README.md) still apply.

The backend sections cover Java packages; the frontend section defines a React-oriented structure rather than copying Java layers. Follow the [testing strategy](testing.md) for test placement and naming.

## Backend feature structure

```text
com.ecommerce.<service>/
├── <Service>Application.java
├── configuration/             # Service properties and composition
├── adapter/web/                # Service-wide REST errors/security, when needed
└── <feature>/
    ├── domain/                 # Business models and business rules
    ├── application/
    │   ├── service/            # Use-case orchestration
    │   ├── port/               # Required abstractions
    │   ├── model/              # Related application input/output models
    │   └── exception/          # Related application errors
    └── adapter/                # Feature-owned incoming and outgoing adapters
        ├── web/
        ├── cli/
        ├── persistence/
        └── ...
```

This tree is illustrative, not a list of mandatory directories. Create a package only when it contains code with a distinct responsibility.

Keep capability-owned code inside its feature even when the service currently has one capability. Service-wide HTTP concerns, such as a global exception handler or security filter chain, may live in root `adapter/web`; feature-specific endpoints stay under that feature's `adapter/web`.

| Package | Responsibility | Typical contents |
| --- | --- | --- |
| `domain` | Business concepts and invariants | Entities, value objects, domain policies and domain errors |
| `application` | Execute use cases and coordinate work | Application services, transaction boundaries, use-case input/output models, interfaces for required external capabilities |
| `adapter` | Translate incoming interactions and implement required technical integrations | Controllers, transport DTOs, CLI commands, message handlers, JDBC repositories and external clients |
| Service `configuration` | Assemble the application | Bean wiring, properties and service-wide framework configuration |

Feature-specific configuration may stay alongside the integration it configures. Do not put business decisions into configuration classes or let a general `configuration` package become a container for unrelated behavior.

## Backend dependency rules

- `domain` must not depend on `application`, adapters, Spring, HTTP or persistence frameworks. Business rules must be understandable and testable without those technologies.
- `application` may depend on `domain` and interfaces describing capabilities it needs. Keep those ports in `application/port` when there is a distinct group. It must not import controllers, transport DTOs or concrete adapters. Spring service and transaction annotations are acceptable; a framework-free application layer is not required.
- Incoming adapters call application use cases. Keep HTTP, console and messaging concerns in their adapter packages; do not bypass use cases to perform business writes directly in a repository.
- Outgoing adapters implement required interfaces and may depend on application/domain types. JDBC, ORM, vendor APIs and protocol-specific models stay at this boundary.
- Composition code may reference both interfaces and implementations to wire them together. This does not allow use cases to reference implementation classes.
- Keep dependencies between features acyclic. Consumers call an explicitly exposed application interface/facade or use-case contract of the owning feature; do not import another feature's persistence implementation or query its tables directly. Within a service, coordinate a necessary cross-feature transaction explicitly. Across services, use approved HTTP/event contracts, never shared table access.

An interface belongs with the inner responsibility that requires it, usually `application/port`; its technical implementation belongs in an adapter package such as `adapter/persistence` or `adapter/storage`. A domain-specific abstraction may belong in `domain` when the business model itself needs it. Do not create matching interface/implementation pairs for every use case, helper or DTO. A concrete application service can be called directly by its incoming adapters.

For example, an administrator-disable use case can require a grant-revocation port implemented by the authorization capability. Preserve the approved atomicity, lock order and rollback behavior when moving that collaboration behind an interface. An asynchronous event is not an equivalent replacement when revocation must commit with account disable.

## Backend subpackages and flexibility

Split a package when it contains distinct responsibilities or navigation and ownership become unclear, rather than at an arbitrary file count. Choose names that explain those responsibilities.

Group related exceptions under the owning layer's `exception` package, such as `admin/application/exception`, rather than mixing a family of error classes with use cases. Domain errors stay with their domain owner; HTTP error advice and serialization belong in `adapter/web`. Do not collect all layers' exceptions into a service-wide generic exception package. Keep distinct types when callers need their semantics; an exception carrying only a free-form message is not a reliable error contract.

For example, a feature may need `adapter/persistence` and `adapter/oauth2`, or `adapter/web` and `adapter/cli`. Another feature may have only a use case and one adapter. They need not have identical trees. Avoid generic packages such as `common`, `utils` or `server` becoming a default destination for unrelated code.

Technical capabilities such as an OAuth2 server need not invent a business domain layer. Framework-specific filters, providers and persistence integrations can be grouped by protocol responsibility under `adapter/oauth2`. Keep their technical dependencies explicit and expose only the collaboration needed by business use cases. Keep package-private collaborators together where that preserves encapsulation.

Simple persistence-backed reads may use a JDBC query adapter without introducing a domain aggregate or a command-style service. Keep SQL in `adapter/persistence`; when a read crosses the application boundary, expose an application-owned query contract and result model. A transport controller should not depend on a concrete JDBC implementation.

Reuse code within its owning feature first. Introduce a shared package or library only when actual consumers and stable shared semantics justify it; do not move unrelated helpers into shared code merely because multiple features use the same technology.

## Frontend organization

Organize React applications by feature, with a small application composition layer and explicitly shared foundations. This is a pragmatic feature-based structure, not a requirement to adopt a full Feature-Sliced Design framework or backend-style domain/application layers.

The current [Backoffice](../../web/backoffice/README.md) is a React/TypeScript/Vite skeleton. Its dependencies include TanStack Router, TanStack Query, React Hook Form and Zod, but business screens, routing and API generation are not yet integrated. The following tree is the convention for that work, not a description of existing folders or authorization to add dependencies.

```text
src/
├── main.tsx
├── app/                       # Providers, router setup, application shell
├── routes/                    # Route definitions and route-level adapters
├── features/
│   └── categories/
│       ├── pages/             # Screen composition
│       ├── components/        # Feature UI
│       ├── api/               # Requests, query keys/options and mutations
│       ├── hooks/             # React workflows with a coherent responsibility
│       └── model/             # Feature types, schemas, pure transformations
├── shared/
│   ├── ui/                    # Business-independent UI primitives
│   ├── api/                   # HTTP transport, common errors, generated client
│   └── lib/                   # Small, genuinely shared technical helpers
└── test/                      # Shared test setup, fixtures and MSW support
```

Create only the folders needed. A small feature may begin with a page and an API file; it need not have every subfolder. A complex screen may colocate its private components, hooks and schemas under `pages/<screen>/`. Move them to feature-level folders only when other screens need them. Feature utilities can live in `model` or a purposefully named subfolder; do not create competing `model`, `types`, `schemas` and `utils` folders without a useful distinction.

### Responsibilities and imports

- `app` owns provider composition, application layout, router initialization and application-wide workflows. `routes` owns route registration, search/parameter validation, loaders and navigation/access adapters. Keep route files small by delegating screen behavior to features. Router-required filenames and generated route trees follow the selected router's conventions.
- Feature pages compose data, interactions and UI. Components may use feature hooks when they own that interaction; a universal split into container and presentational components is not required. Keep transport details and unrelated workflows out of rendering code.
- `api` owns the feature's server-data access, query keys and cache update/invalidation policy. `hooks` owns React-dependent interaction or workflow logic. `model` owns types, validation and transformations that can run without React or browser APIs. A simple page can call a query directly; extract a hook when it provides meaningful reuse or a coherent workflow, not merely to rename a library call.
- Shared UI and helpers must not import features, application composition or route definitions. A component containing category, order or permission-specific business decisions belongs to the owning feature, even if several of its screens use it.
- Features may use `shared` and their own files. Composition code may combine features. When one feature needs another, expose a small intentional entry point, such as exported query options, a lookup component or a model type; avoid deep imports into its private implementation and keep dependencies acyclic. Public entry files are useful at these boundaries, not mandatory barrel files in every folder.
- Keep router coupling at the page/route boundary. Prefer passing validated search values and navigation callbacks into reusable components. Framework-required typed route references may remain in route adapters; they must not spread into shared UI or pure models.
- Put cross-feature cache coordination in a composition workflow or use the owning feature's exported query keys/invalidation function. Do not reproduce another feature's private query-key arrays or mutation rules.

### State and API boundaries

| State or concern | Default owner |
| --- | --- |
| Remote records and request status | TanStack Query cache, through the owning feature's API functions/options |
| Bookmarkable filters, sort, pagination and selection | Validated URL search/path state when appropriate |
| Form values, draft validation and submission feedback | Local form state; use React Hook Form/Zod where useful |
| Dialog visibility, hover/expanded state and temporary interaction | Local component state or a focused hook |
| Truly application-wide client state | A narrowly scoped provider/store when actual consumers justify it |

Do not copy query results into a global store or effect-managed state merely to render them. An editable draft is a legitimate separate state: define when it initializes, resets and handles stale server versions. Derive values when practical rather than synchronizing duplicate state through effects.

Use the shared HTTP transport for base URLs, credentials/CSRF integration and common error normalization. Feature code owns endpoint semantics and maps API data to UI models when needed. Keep generated clients/types in a clearly marked generated directory; do not hand-edit them or introduce wrappers that only duplicate every generated method. Query options, result mapping and stable feature contracts are valid reasons for an adapter. Generated code must not import feature or UI code.

Query keys must include the inputs that distinguish results. Clear or partition identity-sensitive caches when the session changes, and prevent late responses from restoring data from a previous session. This does not imply adding tenant support to the current single-shop scope.

Loading, empty, error, forbidden and mutation-pending behavior belongs with the feature interaction that owns it. Client-side access checks support navigation and presentation; backend authorization remains authoritative. Follow [ADR-0001](../03-architecture/adr/0001-backoffice-bff-as-api-gateway.md): browser code must not own OAuth access/refresh-token storage or rotation. The future BFF integration determines the concrete session/CSRF transport; folder organization does not implement it.

### Growth, tests and supporting files

Split a large page or hook along independently understandable responsibilities, such as query orchestration, an editor form, upload lifecycle or tree interaction. Moving every state variable and handler into one `usePage` hook does not itself improve the separation. File length is a signal to inspect responsibility, not a fixed limit. Avoid both monolithic workflow hooks and one-line wrappers around every library operation.

Keep styles, feature assets and translation namespaces near their owner where tooling permits. Central i18n registration and design tokens can remain application/shared foundations. Do not duplicate primitives or create a cross-application UI package until real consumers and stable semantics justify sharing.

Colocate `*.test.ts(x)` with the page, component, hook or model being tested. Reserve `src/test` for shared setup, factories, render helpers and MSW support, as specified by the [testing strategy](testing.md). Feature-local mock fixtures can stay near their tests; production feature code must not import test support. An explicit development mock bootstrap may compose those fixtures without coupling production behavior to them.

### Reference lessons

The following existing implementations were inspected as examples, not adopted as templates. Links pin the inspected revisions; these observations are limited to the cited code and do not constitute a full repository review.

| Reference | Lesson for this convention |
| --- | --- |
| [Ecommerce Backoffice brand page](https://github.com/XdpsX/ecommerce-backoffice/blob/454110beb0c1ae7d1d2a2847bc7c672ff9424e04/src/features/brands/pages/brands-page.tsx) and [API adapter](https://github.com/XdpsX/ecommerce-backoffice/blob/454110beb0c1ae7d1d2a2847bc7c672ff9424e04/src/features/brands/api/brands.api.ts) | Feature ownership and API/query keys are useful starting points. Keep route coupling at the page boundary and make cache ownership explicit. |
| [Ecommerce Backoffice category structure page](https://github.com/XdpsX/ecommerce-backoffice/blob/454110beb0c1ae7d1d2a2847bc7c672ff9424e04/src/features/categories/pages/category-structure-page.tsx) | A feature folder alone does not separate URL synchronization, tree interaction, edit state and rendering. Review those responsibilities as the screen grows. |
| [Procureflow detail hook](https://github.com/XdpsX/legacy-procureflow/blob/5ad86549768ea4a872b82c48bc6f936f1f7bd503/apps/web/src/features/procurement/pages/hooks/useProcurementDetailPage.ts) | Screen-local organization is useful, but placing quotation editing, vendor selection and operation/session guards into one hook still needs responsibility review. |
| [Procureflow query hooks](https://github.com/XdpsX/legacy-procureflow/blob/5ad86549768ea4a872b82c48bc6f936f1f7bd503/apps/web/src/features/procurement/hooks/useProcurementQueries.ts) | Session-aware cache handling is relevant; direct imports of auth internals and hardcoded receiving query keys demonstrate why cross-feature contracts should be explicit. |

## Adoption and exceptions

Use the relevant backend or frontend structure by default for new features. For existing code, agree on the affected scope and migrate incrementally. Backend package moves must preserve observable contracts, component scanning, bean conditions, transaction proxies and any reflection or serialization references to class names. Frontend moves must preserve route discovery/code generation, aliases, lazy-loading boundaries, public exports and test/mock discovery. Review imports and visibility, and keep tests aligned with the resulting structure.

An alternative structure is acceptable when it fits the capability better. Explain the affected boundary, reason, dependency direction and trade-off in the CR plan or PR. Routine choices inside a package do not require an ADR or separate approval. Changes to service ownership, architectural dependencies or transaction/consistency guarantees need architecture review and the normal scope approval; a folder reorganization does not authorize those changes.

Reviewers should verify responsibility placement and dependency direction, not just directory names. Use targeted dependency tests when they protect an important boundary; no additional architecture tooling is required by this guide.

Record reusable exceptions in the relevant application documentation. Keep temporary migration plans in the locations defined by the [CR workflow](workflow.md).
