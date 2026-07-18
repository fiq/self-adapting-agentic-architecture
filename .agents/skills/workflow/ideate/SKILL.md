---
name: ideate
description: Short-cycle multi-persona session that turns an idea or narrative into a validated change proposal.
---

# Ideate

## Outcome

Turn an idea — or a narrative (prompt or file) — into a validated
`specs/changes/<id>/change.toon` through a short, bounded, multi-persona loop.
Auto-suggest this when a feature request is ambiguous or unspecified.

## Loop

```
knowledge-search ──► team-selection ──► rounds ──► change.toon ──► update-handoff
                                          │
        ┌─────────────────────────────────┼─────────────────────────────────┐
        ▼                 ▼                ▼                 ▼
     Intent           Boundary         Delivery         Quality gate
   product-owner      architect        tech-lead        all active
   domain-expert                                         personas
        └──── one compressed summary + one bounded question per round ────┘
```

- Cap at 3–4 rounds. Seed with a narrative via `narrative-intake` when given.
- Choose participation per round via `agent-team-fallback`: independent
  subagents for high-stakes/ambiguous rounds, `/sudo` persona switches for
  cheap ones. Record which mode was used and what independence was lost.
- Escalate a genuine deadlock to `adversarial-debate`, not another round.

## Rounds

1. **Intent** — who benefits, first valuable behaviour, explicit non-goals.
2. **Boundary** — smallest sufficient architecture, coupling, dependency
   direction; right-size against the calibrated audience (state exclusions,
   get buy-in).
3. **Delivery** — thin slice, first failing boundary test, cheapest check.
4. **Quality gate** — re-check the standing quality rule: reuse over
   duplication, in-path debt, docs-in-change, no silent TODOs.

## Hard choices: persona stance

At a genuinely hard choice, attribute each relevant persona's stance:

```
choice: put persistence behind a port now vs. inline
  architect     encourages  (protects a volatile boundary)
  tech-lead     accepts     (small extra indirection, testable)
  product-owner discourages (no user-visible value this slice)
```

Lead owns synthesis; do not force consensus. Prefer ASCII and bullets; put the
conclusion first.

## Output

- `specs/changes/<id>/` with `proposal.md` (why) and `change.toon` (deltas +
  `WHEN/THEN` scenarios + acceptance + tasks), validated by
  `project check-changes`;
- `HANDOFF.toon` updated;
- knowledge captured or questions recorded via `knowledge-capture`.

## Do not

- Run more than ~4 rounds or debate trivial mechanical changes.
- Pretend sequential `/sudo` passes are independent review.
- Leave the change proposal unvalidated.
