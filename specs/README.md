# Specs

Specs are OpenSpec-shaped, structured-data encoded and agent-first. TOON is the
template default. Generated projects may choose TOON or S-expressions for state
and contracts in `PROJECT_PROFILE.toon.structured_data`; Markdown holds only
rationale.

```
specs/
  capabilities/        living requirements (capability.toon by default)
  changes/<id>/        in-flight proposals
    proposal.md          why (Markdown, rationale)
    design.md            tradeoffs (Markdown, optional)
    change.toon          deltas + WHEN/THEN scenarios + acceptance + tasks
  archive/             completed changes
  templates/           change.toon, capability.toon, spike.md
```

## Flow

```
idea/narrative -> /ideate -> structured change artifact
  -> acceptance test per scenario (boundary-in ATDD)
  -> implement thin slice -> verify -> archive -> update capabilities
```

## Rules

- The structured change artifact is the agent source of truth. Each delta
  carries `WHEN/THEN` scenarios; each scenario maps to an acceptance test in
  `acceptance`.
- Link `relates_to` to knowledge IDs so specs join the knowledge graph.
- Trivial mechanical changes do not need a formal spec.
- Spikes are exploratory prose (`templates/spike.md`).
- TOON benefits state/contracts: readable, diff-friendly, docs-adjacent.
- S-expression benefits rules/compute: compact predicates and transformations.
- No external spec CLI dependency. Markdown export is deferred until a
  non-agent consumer needs it.
