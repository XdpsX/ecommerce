# ADR-0001: Adopt Spring Cloud Gateway as Backoffice BFF

- **Status:** Accepted
- **Date:** 2026-11-02
- **Scope:** Backoffice entry point and authentication architecture
- **Implementation:** Not started; this ADR does not expand infrastructure issue #10

## Context

The Backoffice frontend needs a single backend entry point for authenticated browser requests and routing to independently deployed backend capabilities, including Ecommerce, Identity and Media. The project uses a dedicated Identity service and intends to avoid exposing OAuth2 access tokens to browser JavaScript.

The current Backoffice BFF directory is a placeholder. This ADR records the target design rather than claiming that the gateway or authentication flow is implemented.

## Decision

1. Implement **Backoffice BFF** with **Spring Boot, Spring WebFlux and Spring Cloud Gateway (WebFlux)**. It will also serve as the **Backoffice API Gateway**; do not introduce a separate gateway for this entry point at this stage.
2. The BFF will be an **OAuth2/OIDC confidential client** of the Identity service, using Authorization Code flow (with PKCE where supported), and will own browser-facing login, logout, server-side session handling, API routing and access-token relay to downstream resource servers.
3. Browser authentication will use a **Secure, HttpOnly, appropriately scoped SameSite session cookie**. Access tokens and refresh tokens must not be exposed to browser JavaScript. Protect state-changing requests against CSRF; do not copy the CSRF-disabled configuration from reference projects.
4. Use **Spring Session backed by Redis** for BFF sessions. **PostgreSQL** remains the Identity service's persistent database; the BFF must not share the Identity database as its session store. Configure session TTL, invalidation and logout behavior during implementation.
5. Downstream services must independently validate access tokens and enforce their own authorization rules. The gateway is not the sole authorization boundary.
6. **Storefront BFF is deferred**. No Storefront BFF implementation is authorized by this ADR.

## Alternatives considered

- **Spring Boot + Spring MVC with custom proxy/controllers:** Familiar and simple for aggregation, but requires more custom gateway/routing/token-relay work.
- **Spring Cloud Gateway Server MVC:** Viable gateway alternative without reactive programming; not selected because the Backoffice BFF is primarily an I/O-oriented gateway and the WebFlux variant aligns with the YAS reference.
- **Separate API Gateway plus Backoffice BFF:** Adds another deployable and overlapping responsibility before it is needed.

WebFlux is not assumed to be universally faster than MVC. Its non-blocking I/O model is useful for proxy workloads, but introduces reactive programming and operational complexity.

## Consequences and constraints

- Centralizes Backoffice-facing routing and OAuth2 client/session responsibilities, with Gateway filters such as TokenRelay.
- Introduces Redis availability and session lifecycle concerns, plus a reactive stack for this service only; Identity and domain services do not need to become reactive.
- Validate Spring Boot / Spring Cloud compatibility before selecting versions.
- Review OAuth2/OIDC support, client registrations, redirect URIs, token lifecycle and logout interoperability against the project's actual Identity implementation before coding.
- Define route ownership, CSRF strategy, cookie settings, forwarded headers, CORS and trusted proxy boundaries during implementation.

## Implementation boundary

Infrastructure issue **#10** is restricted to infrastructure work (such as provisioning PostgreSQL/Redis via Compose and related operational configuration). It must **not** implement the BFF, Gateway routes, OAuth2 login, TokenRelay or Spring Session integration. Track those changes in separate implementation issues.

## References

- [YAS Backoffice BFF](https://github.com/nashtech-garage/yas/tree/main/backoffice-bff)
- [Practical Identity](https://github.com/XdpsX/practical-identity)
- [Architecture overview](../README.md)
