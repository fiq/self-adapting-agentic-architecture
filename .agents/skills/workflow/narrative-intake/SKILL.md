---
name: narrative-intake
description: Consume a narrative (prompt or file), interrogate and expand it, and convert it to a TOON change proposal.
---

# Narrative Intake

## Outcome

Turn a free-text narrative into a validated `change.toon` (and, at project
creation, initial `specs/capabilities/`). The narrative may be an inline
prompt or a file (e.g. `CUSTOMIZE_THIS_PROJECT.toon.narrative`).

## Flow

```
narrative (prompt | file)
   ▼ interrogate: intent, users, behaviour, non-goals, unknowns
   ▼ expand:      derive WHEN/THEN scenarios from described outcomes
   ▼ ideate:      brief /ideate rounds where material ambiguity remains
   ▼ convert:     proposal.md (why) + change.toon (deltas + scenarios
                   + acceptance + tasks)
   ▼ validate:    project check-changes
```

## Rules

- Read the whole narrative first; separate stated fact from assumption.
- Ask only the smallest set of questions the narrative cannot answer.
- Every described outcome becomes a `WHEN/THEN` scenario with an acceptance
  test id (the ATDD bridge).
- Record unknowns in the change and, when they outlive it, as `Q-` knowledge.
- At project creation, seed `specs/capabilities/` from durable requirements and
  open a first `specs/changes/<id>/` for the first thin slice.

## Do not

- Invent requirements the narrative does not support.
- Skip validation or leave scenarios without acceptance tests.
