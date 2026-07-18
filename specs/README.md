# Specs

Specs are OpenSpec-shaped, TOON-encoded and agent-first. Structured content is
TOON (validated by `project check-changes`); Markdown holds only rationale.

```
specs/
  capabilities/        living requirements (capability.toon)
  changes/<id>/        in-flight proposals
    proposal.md          why (Markdown, rationale)
    design.md            tradeoffs (Markdown, optional)
    change.toon          deltas + WHEN/THEN scenarios + acceptance + tasks
  archive/             completed changes
  templates/           change.toon, capability.toon, spike.md
```

## Flow

```
idea/narrative -> /ideate -> change.toon (ADDED/MODIFIED/REMOVED + scenarios)
  -> acceptance test per scenario (boundary-in ATDD)
  -> implement thin slice -> verify -> archive -> update capabilities
```

## Rules

- `change.toon` is the agent source of truth. Each delta carries `WHEN/THEN`
  scenarios; each scenario maps to an acceptance test in `acceptance`.
- Link `relates_to` to knowledge IDs so specs join the knowledge graph.
- Trivial mechanical changes do not need a formal spec.
- Spikes are exploratory prose (`templates/spike.md`).
- No external spec CLI dependency. Markdown export is deferred until a
  non-agent consumer needs it.
