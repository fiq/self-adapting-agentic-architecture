# Validation

Validation proves behaviour and repository contract, not just a green command.

## Core Rules

- Do not weaken tests to fit broken production behaviour.
- If a test is wrong, state why and fix the expectation.
- If production behaviour is wrong, keep or strengthen the proving test.
- If the harness is broken, repair it without losing representative coverage.
- Pick the cheapest credible layer for the risk.

## Layers

| Layer | Use |
|---|---|
| Unit/domain | Pure logic, invariants and edge cases |
| Contract | Public API, event, file or schema compatibility |
| Component/integration | Real internal wiring and cheap dependencies |
| E2E/manual | Runtime, UX, platform or operational risks CI cannot prove |

## Architecture Fitness Functions

Fitness functions are deterministic checks for architecture characteristics,
not prose review reminders. Generated projects should identify the top 1-3
architecture risks and encode cheap checks where practical.

Examples:

- dependency direction and forbidden imports;
- public contract/schema drift;
- provider-specific code crossing clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- container health checks and health-aware local dependencies.

Wire automated fitness functions into `.agentic-template/bin/project check`,
`.agentic-template/bin/project ready` or a specialised command they call. When
automation is not yet credible, record the manual validation path and revisit
trigger.

Current project-specific fitness function:

```sh
.agentic-template/bin/project lint
```

It protects the deterministic model boundary by failing when LangChain4j,
picocli, SQLite, Flyway or JMH implementation dependencies appear in
`modules/domain/` or `modules/deterministic/`.

It also confines each provider dependency to one package, so the git, sqlite and
checks adapters cannot import LangChain4j even though they now share a compile
classpath with it, and it fails when a scanned layer directory is missing rather
than reporting OK while scanning nothing.

The CHG-005 retrieval boundary adds an opt-in real-dependency check:

```sh
.agentic-template/bin/project graphrag-integration-test
```

It explicitly starts the pinned Neo4j Community Compose service, runs the
focused projection/traversal/vector/outcome-memory integration test and shuts
the container down while retaining the named rebuildable volume. Ordinary unit,
component and integration commands do not require Docker or Neo4j.

The ordinary integration suite also proves JGit-first clean/dirty revision
identity, Git-visible experiment-envelope/SQLite round trips, generated wiki
projection and historic snapshot cleanup. Native Git fallback is diagnostic;
tests require the normal JGit path so a missing API cannot silently become the
default. The resolved JGit 7.6.0 runtime subtree is limited to JavaEWAH, SLF4J
and Commons Codec and was checked against the published affected ranges for
CVE-2025-4949 and CVE-2023-4759.

A Grype 0.115 scan of the complete installed `saaa/lib` distribution on
2026-08-02 found no high/critical Java finding with an available fix. This
complements the focused JGit advisory check and should be rerun when dependency
pins change.

The Neo4j image was compared with Trivy 0.72 on 2026-08-02. The selected exact
Community UBI10 manifest had zero high/critical operating-system findings; the
current Debian image had 94. Ten high Java findings remain because no released
Neo4j image yet carries their fixed dependency versions. RISK-005 records the
containment and upgrade obligation. Reproduce the scan from the Nix ecosystem:

```sh
nix shell nixpkgs#trivy --command trivy image --scanners vuln \
  --severity HIGH,CRITICAL \
  'neo4j:5.26.28-community-ubi10@sha256:56cdf7d9cf639e9b6bdacd9222758457076de08afbc66f79d0965cb56e1cdc5b'
```

## Required Recording

Record meaningful validation in `HANDOFF.toon.tests_run`.

If validation is skipped or blocked, record:

- command or check;
- reason it did not run;
- risk left open;
- next verification path.

For non-trivial changes, also record the change handoff evidence:

- spec reference, or no-spec rationale;
- fitness-function delta, or no-change rationale;
- knowledge update, proposal or no-record rationale.

## CI Boundary

CI should call repository commands such as `project check`, `project test` and
`project ready`. CI YAML should not duplicate build logic that belongs in the
repository command surface.
