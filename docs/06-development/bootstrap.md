# Bootstrap

`services/ecommerce` is the owner-created baseline. Other service skeletons follow its Java 25 / Spring Boot 4.1.1 stack. `web/backoffice` uses configuration from the reference Backoffice repo, without business pages.

Verify locally: `cd services/ecommerce && ./mvnw test`; repeat for other services. Backoffice: `cd web/backoffice && corepack pnpm install && corepack pnpm dev`. Builds and CI have not been verified. Migrate one module per issue/PR. AI tools are out of scope.
