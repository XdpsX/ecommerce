# Code Quality Foundation — JaCoCo baseline

Each Java service configures JaCoCo 0.8.14 independently. `./mvnw verify` attaches the coverage agent and generates HTML/XML reports under `target/site/jacoco/` (including `jacoco.xml`). GitHub Actions uploads the report as an artifact for seven days.

No coverage threshold or SonarCloud configuration is enabled in this change. Empty/no-test modules may not produce a report; the artifact upload intentionally does not fail for missing files. Review real coverage after tests and business code have been added, then decide thresholds separately.

Check locally from a service directory: `./mvnw -B --no-transfer-progress verify`. Open `target/site/jacoco/index.html` to view coverage if generated.

Note: the project uses Java 25; verify JaCoCo compatibility and CI execution on GitHub before treating reports as validated.
