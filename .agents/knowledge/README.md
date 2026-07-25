# Agent Knowledge

This folder holds durable, reviewed project knowledge for agents and humans.
It complements Superpowers, `AGENTS.md`, the agent team and `HANDOFF.toon`.

```
                    +------------------+
                    |  Canonical Wiki  |
                    |  + Knowledge     |
                    |      Graph       |
                    +---------^--------+
                              | promote
                    +---------+--------+
                    | Knowledge Inbox  |
                    | Learnings        |
                    | Questions        |
                    +---------^--------+
                              | extract
                    +---------+--------+
                    |  HANDOFF.toon    |
                    |  Active State    |
                    +---------^--------+
                              |
                    +---------+--------+
                    |   Agent Team     |
                    | + Superpowers    |
                    +---------^--------+
                              |
                          User Task
```

## What Goes Where

| Place | Use |
|---|---|
| `HANDOFF.toon` | Active task state needed to resume work |
| `knowledge/inbox/` | Unreviewed proposals from recent work |
| `knowledge/questions/` | First-class known unknowns |
| `knowledge/learnings/` | Evidence-backed discoveries not yet general policy |
| Canonical categories | Reviewed durable knowledge with stable IDs |

Canonical categories are `domains`, `systems`, `contracts`, `architecture`,
`decisions`, `patterns`, `risks` and reviewed entries promoted from learnings.

## Promotion Flow

```
ephemeral task state
        |
        v
learning or question proposal
        |
        v
knowledge inbox
        |
        v
evidence, repetition or review
        |
        v
canonical wiki entry
        |
        v
graph relationships
```

Do not promote discoveries directly into canonical knowledge just because a task
completed. Prefer a proposal first unless the entry is already reviewed evidence
or a deliberate decision.

## Search Before Work

Before planning or implementation, search this tree for relevant domain,
system, contract, decision, risk and question entries. Load the smallest useful
set. Report stale entries, conflicts and open questions.

Compact result shape:

```yaml
knowledge_used:
  - id: SYS-001
    reason: target system
conflicts: []
stale_entries: []
open_questions: []
```

## Handoff Gate

After meaningful work, update `HANDOFF.toon.knowledge` with:

- `consulted`: IDs or paths used;
- `proposals`: knowledge entries created or updated;
- `no_record`: a concrete reason if no durable graph update was warranted.

`project check-handoff` enforces that this checkpoint exists.

## Superpowers Boundary

Superpowers provides the engineering workflow: brainstorming, planning, TDD,
debugging, implementation, review and verification.

This repository adds local context: who should participate, what project
knowledge exists, what changed, what was learned and what should survive the
session.

## Adding A Knowledge Type

1. Add a folder under `.agents/knowledge/`.
2. Add a concise template in `.agents/knowledge/templates/`.
3. Add the type and ID prefix to `.agents/schemas/knowledge-entry.schema.md`.
4. Update `.agentic-template/bin/check-knowledge` if validation rules change.
5. Link the new type from `.agents/knowledge/index.md`.

## Stale Entry Review

Entries with `status: canonical` should include `reviewed_at` and
`review_after`. When `review_after` passes, treat the entry as stale until a
human or knowledge curator refreshes evidence and updates the date.
