# Operations

SAAA is a local CLI. `nix develop` supplies Java, Gradle, the Docker client and
Compose; it uses the host Docker daemon. `nix run . -- <saaa-command>` builds
incrementally and invokes the same installed Java binary. Devenv is not an
additional requirement.

The optional GraphRAG topology is one pinned Neo4j Community container with a
health check and named `neo4j-data` volume. Ordinary `docker compose down`
retains the volume. The volume is outside Git and disposable: repository facts
come from Git and outcome memory comes from `experiments/ledger/*.toon` through
the efficient `.saaa/experiments.sqlite` ledger. `.saaa/retrieval.sqlite` is a
disposable embedding/capsule/provenance projection.

The image is the official 5.26.28 Community UBI10 manifest pinned by digest.
HTTP/HTTPS is disabled and authenticated Bolt is bound only to loopback. The
latest scan and residual Java dependency findings are recorded in RISK-005;
this topology is not suitable for remote or untrusted exposure.

```sh
export SAAA_NEO4J_PASSWORD='choose-a-local-password'
docker compose up -d --wait neo4j
./modules/cli/build/install/saaa/bin/saaa saaa-index build --repository . --role SUBJECT_AND_PROCESS
./modules/cli/build/install/saaa/bin/saaa saaa-index status --repository .
docker compose down
```

Use `--role SUBJECT` for the implementation repository and `--role PROCESS` for
the SAAA repository when they differ. Both live in one repository-partitioned
graph. An evolution envelope records both revisions. `SAAA_PROCESS_REPOSITORY`
selects the process checkout; it defaults to the subject for self-evolution.

Historic inflation is explicit and replaces the selected repository's graph
projection without changing the checkout:

```sh
./modules/cli/build/install/saaa/bin/saaa saaa-reinflate --repository . --revision <commit>
```

JGit performs read-only repository access without user setup. If it cannot open
an unusual repository layout, SAAA prints a warning before using native Git as a
compatibility fallback.

Do not add cloud emulators, Kubernetes or service dependencies without evidence.
