# Fitness Taxonomy Step 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record the colliding fitness and benchmark terminology, the naming prefix scheme and the severity classes, with an automated check that keeps the glossary complete.

**Architecture:** A new `check-glossary` script mirrors the existing `check-readme` `REQUIRED_SECTIONS` pattern and joins the `project check` list, so glossary drift fails CI. The glossary gains entries for the colliding terms and links to a new `CON-002` knowledge entry, which holds the normative naming scheme and severity classes. No Java changes, no gate-id rename.

**Tech Stack:** Python 3 (repository tooling convention), Markdown with YAML frontmatter for knowledge entries.

## Global Constraints

- Documentation only. Do **not** rename any `hard_gate_*` identifier in this slice: `EvolveMcpResponseSerializer` partitions MCP output by the `hard_gate_` prefix and sorts by key, `EvolveMcpToolTest` asserts that ordering, and the strings are already persisted in `.saaa/experiments.sqlite` and committed `experiments/ledger/*.toon` envelopes.
- No Java source changes. No changes to `PhenotypeFitnessScorer`, `MutationOperatorPolicy`, weights or thresholds.
- Knowledge entries must satisfy `.agentic-template/bin/check-knowledge`: `type: contract` requires a `CON-` id prefix, `status` must be one of `canonical, proposed, experimental, deprecated, superseded, stale, open, investigating, answered, deferred, obsolete`, and every id in a relation field must resolve to an existing entry.
- `.agentic-template/bin/project check` and `.agentic-template/bin/project ready` must pass before the final commit.
- Real commit author and committer dates. Do not set `GIT_AUTHOR_DATE`, `GIT_COMMITTER_DATE` or `--date`.
- Work on a bounded branch and integrate by PR. `main` is at `34d1c5f`.
- Source of truth for content decisions: `docs/superpowers/specs/2026-08-04-fitness-taxonomy-and-steered-evolution-design.md`, sections 1 and 12.

---

### Task 1: Glossary check and the colliding terms

**Files:**
- Create: `.agentic-template/bin/check-glossary`
- Modify: `.agentic-template/bin/project:10-23` (COMMANDS table)
- Modify: `docs/wiki/glossary.md`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `.agentic-template/bin/check-glossary`, exit 0 on success and 1 on failure, printing `GLOSSARY OK` or `GLOSSARY CHECK FAILED`. Task 2 extends its `REQUIRED_REFERENCES` list.

- [ ] **Step 1: Write the failing check**

Create `.agentic-template/bin/check-glossary`:

```python
#!/usr/bin/env python3
"""Fail when the glossary omits a term the project has decided is load-bearing.

A term lands in REQUIRED_TERMS when two established usages collide and the wiki
has to say so explicitly rather than silently pick a winner. This mirrors the
REQUIRED_SECTIONS shape in check-readme.
"""
from pathlib import Path
import re
import sys

ROOT = Path.cwd()
GLOSSARY = ROOT / "docs/wiki/glossary.md"

REQUIRED_TERMS = [
    "Architecture Fitness Function",
    "Benchmark",
    "Candidate Fitness Function",
    "Corpus",
    "Invariant",
    "Objective",
    "Severity Class",
]

REQUIRED_REFERENCES = []


def fail(lines):
    print("GLOSSARY CHECK FAILED")
    print()
    for line in lines:
        print("- " + line)
    return 1


def main():
    if not GLOSSARY.exists():
        return fail(["docs/wiki/glossary.md missing"])

    text = GLOSSARY.read_text()
    errors = []
    for term in REQUIRED_TERMS:
        pattern = re.compile(rf"^##\s+{re.escape(term)}\s*$", re.MULTILINE)
        if not pattern.search(text):
            errors.append(f"glossary missing term heading: {term!r}")
    for reference in REQUIRED_REFERENCES:
        if reference not in text:
            errors.append(f"glossary missing required reference: {reference!r}")
    if errors:
        return fail(errors)
    print("GLOSSARY OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

Make it executable:

```bash
chmod +x .agentic-template/bin/check-glossary
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.agentic-template/bin/check-glossary`

Expected: exit 1, `GLOSSARY CHECK FAILED`, listing all seven missing term headings.

- [ ] **Step 3: Add the glossary entries**

`docs/wiki/glossary.md` is alphabetically ordered. Insert each new section at the position given below, keeping that order intact:

| New section | Insert after |
|---|---|
| `## Architecture Fitness Function` | `## Agentic Loop` |
| `## Benchmark` | `## Architecture Fitness Function` |
| `## Candidate Fitness Function` | `## Benchmark` |
| `## Corpus` | `## Change` |
| `## Invariant` | `## Corpus` |
| `## Objective` | `## Knowledge Node` |
| `## Severity Class` | `## Ontology` |

Note `## Capability` and `## Change` sit between `Candidate Fitness Function` and `Corpus`, and `## Northbound Interface` sits between `Objective` and `Ontology`, so the inserts interleave with existing entries rather than forming one block.

```markdown
## Architecture Fitness Function

An automated check that a structural characteristic of SAAA itself still holds,
in the sense used by *Building Evolutionary Architectures*.
`.agentic-template/bin/check-architecture-boundaries`, run by
`project lint`, is the first one: model-provider imports may not appear in
`modules/domain` or `modules/deterministic`, and the check fails when a scanned
layer directory is missing so a rename cannot make it pass vacuously.

It grades the repository, never a candidate. Distinct from a Candidate Fitness
Function, which grades a candidate and shares nothing with this beyond the name.

## Benchmark

A measurement instrument. In SAAA a benchmark is a JMH microbenchmark producing
`BenchmarkEvidence(name, value, unit)`, which is one input to one objective. A
benchmark never decides anything.

The genetic-programming literature often uses "benchmark" for a fixed evaluation
suite. SAAA calls those Corpora and reserves "benchmark" for the instrument.

## Candidate Fitness Function

`PhenotypeFitnessScorer`: the hard gates, weighted objectives and promotion
threshold that turn evidence about one candidate into `PROMOTE` or `DISCARD`.

It grades a candidate, never the repository. Distinct from an Architecture
Fitness Function.

## Corpus

A fixed evaluation suite. `GoldenCorpus.java` is a regression corpus for the
Candidate Fitness Function; the `saaa-ablate retrieval` TSV is a corpus of task
rows. The genetic-programming literature would call these benchmarks; see
Benchmark for why SAAA does not.

## Invariant

A property a candidate must satisfy. Binary for the promote-or-discard decision
and not tradeable against any objective, but carrying a magnitude used to rank
candidates that have already failed, so a near miss stays distinguishable from a
total miss.

## Objective

A graded property that compounds into a candidate's score, evaluated only once
every invariant has passed.

## Severity Class

The partition that orders invariant violations and decides who may set a
threshold: integrity, safety, correctness, shape. Integrity voids a run rather
than ranking it, because a compromised measurement is not a statement about
fitness.
```

- [ ] **Step 4: Run the check to verify it passes**

Run: `.agentic-template/bin/check-glossary`

Expected: exit 0, `GLOSSARY OK`.

- [ ] **Step 5: Wire the check into the project command surface**

In `.agentic-template/bin/project`, add a standalone command entry alongside the other `check-*` entries, and add the script to the `check` list. The `check` list currently ends with `check-mcp`:

```python
    "check": [
        [str(BIN / "check-repo-contract")],
        [str(BIN / "check-project-profile")],
        [str(BIN / "check-handoff")],
        [str(BIN / "check-knowledge")],
        [str(BIN / "check-changes")],
        [str(BIN / "check-architecture-boundaries")],
        [str(BIN / "check-tooling")],
        [str(BIN / "check-mcp")],
        [str(BIN / "check-glossary")],
    ],
```

And alongside `"check-wiki"`:

```python
    "check-glossary": [[str(BIN / "check-glossary")]],
```

- [ ] **Step 6: Run the full check to verify nothing regressed**

Run: `.agentic-template/bin/project check`

Expected: exit 0, with `GLOSSARY OK` printed after `MCP STATUS`. All existing lines (`REPO CONTRACT OK`, `PROJECT PROFILE OK`, `HANDOFF OK`, `KNOWLEDGE OK`, `CHANGES OK`, `ARCHITECTURE BOUNDARIES OK`) still present.

CI needs no change: `.github/workflows/ci.yml` already runs `project check`.

- [ ] **Step 7: Commit**

```bash
git add .agentic-template/bin/check-glossary .agentic-template/bin/project docs/wiki/glossary.md
git commit -m "Define the colliding fitness and benchmark terms in the glossary

Three concepts shared two names. \"Fitness function\" meant both candidate
scoring in PhenotypeFitnessScorer and architecture conformance in
check-architecture-boundaries, and \"benchmark\" meant both a JMH measurement
and, in the GP literature, an evaluation suite.

Adds check-glossary, mirroring the REQUIRED_SECTIONS shape in check-readme,
so a term the project has decided is load-bearing cannot silently vanish.
Wired into project check, which CI already runs."
```

---

### Task 2: CON-002 records the naming scheme and severity classes

**Files:**
- Create: `.agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md`
- Modify: `.agentic-template/bin/check-glossary` (the `REQUIRED_REFERENCES` list created in Task 1)
- Modify: `docs/wiki/glossary.md` (link the four scheme-dependent terms to `CON-002`)

**Interfaces:**
- Consumes: `check-glossary` from Task 1, specifically its `REQUIRED_REFERENCES` list.
- Produces: knowledge id `CON-002`, referenced by the glossary and available for `relates_to` in later specs.

- [ ] **Step 1: Write the failing assertion**

In `.agentic-template/bin/check-glossary`, change:

```python
REQUIRED_REFERENCES = []
```

to:

```python
REQUIRED_REFERENCES = [
    "CON-002",
]
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.agentic-template/bin/check-glossary`

Expected: exit 1, `GLOSSARY CHECK FAILED`, listing `glossary missing required reference: 'CON-002'`.

- [ ] **Step 3: Create the knowledge entry**

Create `.agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md`:

```markdown
---
id: CON-002
type: contract
title: Fitness identifier naming and severity classes
status: proposed
summary: Fitness identifiers carry a subject/process and invariant/objective prefix, and invariant violations are partitioned into integrity, safety, correctness and shape.
owners:
  - architect
relates_to:
  - ARCH-001
  - CON-001
risks:
  - RISK-002
evidence:
  - docs/superpowers/specs/2026-08-04-fitness-taxonomy-and-steered-evolution-design.md
  - docs/wiki/glossary.md
review_after: 2027-02-04
---

# Fitness Identifier Naming and Severity Classes

## Naming

A fitness identifier names whose property it is and which force it carries:

```
subject.invariant.<name>     must hold on the candidate
subject.objective.<name>     compounds into the candidate score
process.invariant.<name>     must hold on SAAA itself
```

`subject` and `process` reuse the vocabulary already carried by
`RepositoryRole` and by the `subject_repository_id` and
`process_repository_revision` fields of the experiment envelope, rather than
introducing a parallel scheme.

The prefix encodes kind, not type. It exists because "fitness function" had
come to mean both candidate scoring and architecture conformance, and
`HANDOFF.toon` had begun mixing both under one `fitness_functions` key.

The existing `hard_gate_*` identifiers do not yet follow this scheme. Renaming
them changes MCP output ordering, which `EvolveMcpToolTest` asserts, and
invalidates identifiers already persisted in `.saaa/experiments.sqlite` and in
committed `experiments/ledger/*.toon` envelopes. The rename is therefore a
separate change with a migration story, and this entry records the target shape
rather than claiming it is in force.

## Severity classes

Invariant violations are partitioned by what the violation costs and whether it
can be undone. The same partition decides who may set a threshold, because the
less reversible the consequence the more human authority it warrants.

| Class | Test | Orders | Threshold set by |
|---|---|---|---|
| integrity | can we trust this measurement at all? | voids, does not rank | human only |
| safety | could this harm something outside the experiment? | 1st | human only |
| correctness | does it do the job? | 2nd | human, or quorum ratified |
| shape | is it well-formed? | 3rd | quorum, persisted, modifiable |

Integrity voids a run rather than ranking it. A candidate that rewrote the
script grading it, produced no evidence, realised nothing, or returned missing
or out-of-range objective scores has not made a statement about fitness; it has
reported that the measurement cannot be trusted. Such runs stay out of the
evolutionary archive and never become exemplars, whereas a measured failure is a
useful near miss and belongs there.

`process.invariant.layer_boundaries` is correctness rather than shape, because
Gradle already makes a layer violation a compile error.

Comparison is lexicographic: worst class violated decides first, magnitude
within the class breaks ties. Magnitudes are never summed across classes, so no
exchange rate between incommensurable violations is ever needed.
```

- [ ] **Step 4: Link the scheme-dependent glossary terms**

In `docs/wiki/glossary.md`, append a sentence to each of the four entries that depend on the scheme.

To `## Invariant`, append:

```markdown
Named `subject.invariant.*` or `process.invariant.*`; see `CON-002`.
```

To `## Objective`, append:

```markdown
Named `subject.objective.*`; see `CON-002`.
```

To `## Severity Class`, append:

```markdown
Defined in `CON-002`, which also gives the comparison order and the threshold
authority for each class.
```

To `## Candidate Fitness Function`, append:

```markdown
Its identifiers follow the naming scheme in `CON-002`.
```

- [ ] **Step 5: Run the checks to verify they pass**

Run: `.agentic-template/bin/check-glossary`

Expected: exit 0, `GLOSSARY OK`.

Run: `.agentic-template/bin/check-knowledge`

Expected: exit 0, `KNOWLEDGE OK`. This validates the `CON-` prefix against `type: contract`, the `proposed` status value, and that `ARCH-001`, `CON-001` and `RISK-002` all resolve.

Run: `.agentic-template/bin/project check-wiki`

Expected: `WIKI OK`. The glossary now references `CON-002`, which must resolve.

- [ ] **Step 6: Commit**

```bash
git add .agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md .agentic-template/bin/check-glossary docs/wiki/glossary.md
git commit -m "Record the fitness naming scheme and severity classes as CON-002

Identifiers carry subject/process and invariant/objective prefixes, reusing
the RepositoryRole vocabulary the experiment envelope already has rather than
inventing a parallel scheme.

Severity classes partition violations by what they cost and whether they can
be undone, which also yields the threshold authority. Integrity voids a run
rather than ranking it, so compromised measurements stay out of the archive
and never become exemplars.

Records the target shape rather than claiming it is in force: the existing
hard_gate_* identifiers are not renamed here, because that changes MCP output
ordering and invalidates identifiers already persisted in SQLite and in
committed ledger envelopes."
```

---

### Task 3: Handoff and integration

**Files:**
- Modify: `HANDOFF.toon` (`session`, `completed`, `readme_evidence_pass_files_changed` sibling, `tests_run`, `knowledge`)

**Interfaces:**
- Consumes: `CON-002` from Task 2.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Update the handoff**

In `HANDOFF.toon`, set `session.branch` to the working branch name and `session.status` to `fitness_taxonomy_step_1`.

Add to `completed`:

```
  - implemented step 1 of the fitness taxonomy design: `check-glossary` enforcing seven load-bearing terms, glossary entries separating candidate fitness from architecture fitness and benchmark from corpus, and `CON-002` recording the `subject`/`process` plus `invariant`/`objective` naming scheme and the integrity/safety/correctness/shape severity classes
  - deliberately did not rename `hard_gate_*` identifiers: the rename changes MCP output ordering asserted by `EvolveMcpToolTest` and invalidates identifiers already persisted in `.saaa/experiments.sqlite` and committed `experiments/ledger/*.toon` envelopes, so it needs its own change with a migration story
```

Add to `knowledge.proposals`:

```
    - .agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md
```

Add to `tests_run`:

```
  - 2026-08-04 `.agentic-template/bin/check-glossary`: GLOSSARY OK
  - 2026-08-04 `.agentic-template/bin/project check`: pass including the new glossary check
  - 2026-08-04 `.agentic-template/bin/project ready`: READY: PASS
```

- [ ] **Step 2: Run the full readiness gate**

Run: `.agentic-template/bin/project check`

Expected: exit 0, all checks OK including `GLOSSARY OK`.

Run: `.agentic-template/bin/project ready`

Expected: `READY: PASS`, 0 failed.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 3: Commit and open a PR**

```bash
git add HANDOFF.toon
git commit -m "Refresh the handoff after fitness taxonomy step 1"
git push -u origin docs/fitness-taxonomy-step-1
gh pr create --base main --head docs/fitness-taxonomy-step-1 \
  --title "Define the fitness taxonomy: glossary, naming scheme and severity classes"
```

The branch name is `docs/fitness-taxonomy-step-1`, created from `main` at `34d1c5f` before Task 1 Step 1.

Note: `origin` is an SSH remote. If a push hangs, it is not this repository's configuration.

---

## Notes for the implementer

**Why no Java test.** This slice changes no runtime behaviour, so there is no
Java test to write. The check script is the test: it fails before the
documentation exists and passes after, and CI runs it through `project check` on
every PR.

**Why the rename is excluded.** `EvolveMcpResponseSerializer` sorts measured
objective keys and then hard gates by key, and `EvolveMcpToolTest` asserts that
ordering is stable. Renaming `hard_gate_deterministic_checks` to
`subject.invariant.deterministic_checks` moves it out of the `hard_gate_`
partition and changes the serialised order. Separately, `fitness_objectives`
rows in `.saaa/experiments.sqlite` and `experiments/ledger/*.toon` envelopes
already hold the old strings, so a rename without migration makes historical
records incomparable with new ones. That is a change worth doing and worth doing
carefully, and it is not this one.

**What is deliberately still open.** `CON-002` carries `status: proposed`, not
`canonical`, because the scheme has not been exercised against a real rename.
The design note lists six open questions that this slice does not answer,
including which of today's four gates maps to which severity class.
