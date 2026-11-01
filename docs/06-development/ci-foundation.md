# CI Foundation

## Scope
Independent GitHub Actions workflows for four Maven services and Backoffice; Dependabot for Maven, npm/pnpm and GitHub Actions. No Docker publish or deployment.

## Validation
- Backend: `./mvnw -B --no-transfer-progress verify` in each service; Spotless check is bound to Maven validate.
- Backoffice: `pnpm install --frozen-lockfile`, `pnpm format:check`, `pnpm lint`, `pnpm test`, `pnpm build`.
- Java 25, Node 24 and pnpm 12.9.1 are configured.
- Backend JUnit XML reports are retained for 7 days.
- Workflows use path filters and support manual dispatch.

## Review notes
- GitHub Actions have not been executed in this change. Validate on GitHub before requiring checks.
- Path-filtered workflows may remain Pending if marked as required checks and skipped. Decide branch protection strategy separately.
- The current Backoffice lockfile should be verified for consistency with package.json before enabling mandatory checks.
- Dependabot PRs are review-only; do not auto-merge.
- Follow-ups: CodeQL, coverage thresholds, dependency/secret scanning, Docker image scan/publish and release policy.
