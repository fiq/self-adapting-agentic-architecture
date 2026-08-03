---
id: RISK-005
type: risk
title: Local Neo4j image contains known Java dependency findings
status: experimental
summary: The safest current official Community image has no high or critical UBI package findings, but includes ten high Java dependency findings whose fixed versions are not yet shipped by Neo4j.
owners:
  - architect
relates_to:
  - ARCH-002
  - SYS-001
decisions:
  - ADR-0004
evidence:
  - compose.yaml
  - docs/validation.md
review_after: 2026-08-31
---

# Local Neo4j Supply-chain Risk

On 2026-08-02, Trivy 0.72 found 94 high/critical operating-system findings plus
Java findings in `neo4j:2026.06.0`. The official
`neo4j:5.26.28-community-ubi10` alternative removed all high/critical operating
system findings and reduced the Java result to ten unique high findings. The
published fixed Java versions are newer than those bundled by every current
Neo4j release, so swapping individual jars inside the database distribution is
not treated as a supported fix.

The experiment pins the UBI10 multi-architecture manifest by digest, disables
Neo4j HTTP and HTTPS, publishes only authenticated Bolt on `127.0.0.1`, installs
no plugins and sets `no-new-privileges`. Ordinary `NONE` runs do not start it.
This is containment, not a claim that the findings disappeared. Re-scan when
Neo4j publishes a patched image; upgrade and rerun graph integration before
closing this risk. Do not expose this topology to a remote or untrusted network.
