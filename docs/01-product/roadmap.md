# Product roadmap (proposed)

This document describes intended milestones and sequencing, **not** completed functionality or live progress. The monorepo is currently in bootstrap; application skeletons are not evidence of migrated business features. GitHub Milestones and Projects are the source of truth for issue progress.

| Milestone | Intended outcome |
| --- | --- |
| M0 — Platform Foundation & Repository Bootstrap | Monorepo skeleton, CI, developer workflows, documentation and testing conventions |
| M1 — Ecommerce Domain Renewal | Rebuild and validate core ecommerce domain capabilities through backend and Backoffice vertical slices |
| M2 — Storefront & Shopping Experience | Customer-facing browsing, cart and checkout experiences |
| M3 — Marketplace & Multi-Vendor | Seller/shop ownership, permissions and marketplace capabilities |
| M4 — Distributed Systems & Platform Evolution | Reliability, observability, events and advanced platform engineering |

M2–M4 are directional proposals, not approved delivery commitments. Do not assume all capabilities exist in the current repository.

## Proposed M1 sequence

1. **Foundation validation and Category pilot:** confirm API, test infrastructure and Backoffice conventions with one complete vertical slice.
2. **Catalog management:** Brand, Product and Media.
3. **Inventory and pricing:** SKU, stock and pricing behavior.
4. **Customer and cart:** customer-facing state and cart domain.
5. **Order and checkout:** order lifecycle and integration boundaries.
6. **Hardening:** security, contract verification, E2E and documentation review.

Before committing to this scope, audit the existing reference repositories and current monorepo implementation. Refine only the next few CRs into actionable issues; revisit sequencing as dependencies become clear. Avoid implementing full multi-vendor scope in M1 solely for future-proofing.
