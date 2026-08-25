# Validation

Validation proves that behaviour and the repository contract hold. A green
command on its own is not proof.

## Core Rules

- Do not weaken tests to fit broken production behaviour.
- If a test is wrong, state why and fix the expectation.
- If production behaviour is wrong, keep or strengthen the proving test.
- If the harness is broken, repair it without losing representative coverage.
- Pick the cheapest credible layer for the risk.

## Layers

A layer here is a level a test can run at, from cheap and fast up to the
risks CI cannot prove. Use the cheapest layer that credibly covers the risk:

```
  cheapest

   unit/domain            pure logic, invariants and edge cases
   contract               public API, event, file or schema compatibility
   component/integration  real internal wiring and cheap dependencies
   E2E/manual             runtime, UX, platform or operational risks
                          CI cannot prove
```

## Architecture Fitness Functions

An architecture fitness function is a small deterministic check that guards an
architecture characteristic. It runs and fails like a test; it is not a prose
review reminder. Generated projects should identify the top 1-3 architecture
risks and encode cheap checks where practical.

Typical things a fitness function checks:

- dependency direction and forbidden imports;
- public contract/schema drift;
- provider-specific code crossing clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- container health checks and health-aware local dependencies.

Where fitness functions are wired:

- automated fitness functions go into `.agentic-template/bin/project check`,
  `.agentic-template/bin/project ready` or a specialised command they call;
- when automation is not yet credible, record the manual validation path and
  a revisit trigger.

## Checks in This Project

### Boundary lint

```sh
.agentic-template/bin/project lint
```

`project lint` protects the deterministic model boundary. It:

- fails when LangChain4j, picocli, SQLite, Flyway or JMH implementation
  dependencies appear in `modules/domain/` or `modules/deterministic/`;
- confines each provider dependency to one package, so the git, sqlite and
  checks adapters cannot import LangChain4j even though they now share a
  compile classpath with it;
- fails when a scanned layer directory is missing, rather than reporting OK
  while scanning nothing.

### GraphRAG integration check (CHG-005)

```sh
.agentic-template/bin/project graphrag-integration-test
```

An opt-in, real-dependency check added for the CHG-005 retrieval boundary. It:

- explicitly starts the pinned Neo4j Community Compose service;
- runs the focused projection/traversal/vector/outcome-memory integration
  test;
- shuts the container down while retaining the named rebuildable volume.

Ordinary unit, component and integration commands do not require Docker or
Neo4j.

### What the ordinary integration suite proves

- JGit-first clean/dirty revision identity;
- Git-visible experiment-envelope/SQLite round trips;
- generated wiki projection;
- historic snapshot cleanup.

Native Git fallback is diagnostic only: the tests require the normal JGit
path, so a missing API cannot silently become the default.

### Dependency and image scans on record

- JGit: the resolved JGit 7.6.0 runtime subtree is limited to JavaEWAH, SLF4J
  and Commons Codec and was checked against the published affected ranges for
  CVE-2025-4949 and CVE-2023-4759.
- Java distribution: a Grype 0.115 scan of the complete installed `saaa/lib`
  distribution on 2026-08-02 found no high/critical Java finding with an
  available fix. This complements the focused JGit advisory check and should
  be rerun when dependency pins change.
- Neo4j image: compared with Trivy 0.72 on 2026-08-02. The selected exact
  Community UBI10 manifest had zero high/critical operating-system findings;
  the current Debian image had 94. Ten high Java findings remain because no
  released Neo4j image yet carries their fixed dependency versions. RISK-005
  records the containment and upgrade obligation.

Reproduce the Neo4j scan from the Nix ecosystem:

```sh
nix shell nixpkgs#trivy --command trivy image --scanners vuln \
  --severity HIGH,CRITICAL \
  'neo4j:5.26.28-community-ubi10@sha256:56cdf7d9cf639e9b6bdacd9222758457076de08afbc66f79d0965cb56e1cdc5b'
```

## Required Recording

Record meaningful validation in `HANDOFF.toon.tests_run`.

If validation is skipped or blocked, record:

- the command or check;
- the reason it did not run;
- the risk left open;
- the next verification path.

For non-trivial changes, also record the change handoff evidence:

- spec reference, or no-spec rationale;
- fitness-function delta, or no-change rationale;
- knowledge update, proposal or no-record rationale.

## CI Boundary

- CI should call repository commands such as `project check`, `project test`
  and `project ready`.
- CI YAML should not duplicate build logic that belongs in the repository
  command surface.
