# Structured Data Formats

Two formats carry the structured information here: TOON and S-expressions.
Generated projects choose semantic formats in `CUSTOMIZE_THIS_PROJECT.toon`
and record the resolved policy in `PROJECT_PROFILE.toon`.

The choice in one view:

```
"What is true or required?"              ->  TOON          (state and contracts)
"Given these facts, what should happen?" ->  S-expression  (rules and compute)
```

| Purpose | Default | Benefit |
|---|---|---|
| State and contracts | TOON | Human-readable, diff-friendly, close to Markdown docs |
| Rules and compute | S-expression | Compact, regular predicates and transformations |

## State and contracts

Facts that answer "what is true or required?":

- project profile, handoff state, decisions and unknowns;
- capability specs, API/file/event contracts and acceptance mappings;
- knowledge entries and ADR metadata.

## Rules and compute

Logic that answers "given these facts, what should happen?":

- validation gates and command selection;
- model routing, role selection and escalation predicates;
- architecture boundary checks and dependency rules;
- query/filter expressions over specs or knowledge.

## Policy

- Keep template control files as TOON unless project tooling is specialised.
- Use one semantic format per artifact.
- Record deliberate deviations in `PROJECT_PROFILE.toon.decisions`.

## Recorded deviations

Two files are recorded as deliberate deviations from the policy today. Each
entry says why and when it retires.

- `.saaa/fixture-mutation.txt` — the fixture proposer's canned mutation — is
  plain text, not TOON.
  - The format is deliberately trivial: the first line is the summary, the
    remainder is the proposed new file content.
  - The deviation is scoped to the fixture proposer.
  - It retires when `CHG-002` task `T3d` adds the TOON envelope reader.
- `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/GoldenCorpus.java`
  — the golden-verdict corpus for `PhenotypeBridgeScorer` (CHG-004 T8) — is a
  Java constant list of records rather than TOON fixture files.
  - Same reason: no Java TOON reader yet.
  - The persona-review pass on CHG-004 pinned the corpus format to TOON, so
    this deviation is intended to retire when `CHG-002` task `T3d` lands the
    envelope reader: the entry list migrates to
    `modules/deterministic/src/test/resources/golden-corpus/*.toon` and the
    test iterates the loaded files.
  - Until then the "checked in, treated as immutable, editing requires a spec
    change with rationale" invariants apply to the Java source file, same as
    they would to a TOON fixture.
