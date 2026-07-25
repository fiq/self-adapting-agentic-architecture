# Structured Data Formats

Generated projects choose semantic formats in `CUSTOMIZE_THIS_PROJECT.toon` and
record the resolved policy in `PROJECT_PROFILE.toon`.

| Purpose | Default | Benefit |
|---|---|---|
| State and contracts | TOON | Human-readable, diff-friendly, close to Markdown docs |
| Rules and compute | S-expression | Compact, regular predicates and transformations |

## State and contracts

Use for facts that answer "what is true or required?"

- project profile, handoff state, decisions and unknowns
- capability specs, API/file/event contracts and acceptance mappings
- knowledge entries and ADR metadata

## Rules and compute

Use for logic that answers "given these facts, what should happen?"

- validation gates and command selection
- model routing, role selection and escalation predicates
- architecture boundary checks and dependency rules
- query/filter expressions over specs or knowledge

## Policy

- Keep template control files as TOON unless project tooling is specialised.
- Use one semantic format per artifact.
- Record deliberate deviations in `PROJECT_PROFILE.toon.decisions`.
