---
name: sudo
description: Perform an operation as a specific agent persona (role or subagent) for a bounded task.
---

# /sudo

## Outcome

Switch the agent's active persona to a named team role or subagent, perform
a single bounded operation from that persona's perspective, then return to
the lead agent persona. This is role-switching, not spawning a new agent.

## Usage

```
/sudo <persona> <operation>
```

`<persona>` is the name of a persistent team role or bounded subagent:

### Persistent team roles

- `product-owner`
- `architect`
- `tech-lead`
- `domain-expert`
- `knowledge-curator`

Project-specific personas created by `/specialise` (e.g. `compliance-officer`,
`end-user-novice`, `on-call-sre`) also live in `.agents/team/` and are valid
`/sudo` targets.

### Bounded subagents

- `researcher`
- `repository-explorer`
- `red-team-reviewer`
- `evidence-checker`
- `test-reviewer`
- `security-reviewer`
- `performance-reviewer`

## Procedure

1. Read the persona definition from `.agents/team/<persona>.md` or
   `.agents/subagents/<persona>.md`.
2. Adopt the persona's responsibilities, questions owned, inputs and outputs
   for the duration of the operation.
3. Perform the requested operation from that persona's perspective.
4. Produce output in the persona's expected format (see the persona file for
   expected output structure).
5. Return to the lead agent persona after the operation is complete.
6. Record the persona switch in `HANDOFF.toon` if the operation produced
   material decisions, findings or evidence.

## Do not

- Use `/sudo` to bypass review gates — the persona's non-responsibilities
  still apply.
- Chain multiple `/sudo` calls to simulate independent review. Sequential
  role passes by the same agent are not equivalent to independent review.
- Use `/sudo` for trivial mechanical edits that do not benefit from a
  persona shift.
- Skip recording material findings in `HANDOFF.toon` or knowledge entries.

## Example

```
/sudo architect review the persistence boundary between the API and database layers
```

This reads `.agents/team/architect.md`, adopts the architect persona,
reviews the persistence boundary, produces architecture findings with
evidence and confidence, then returns to the lead agent persona.

## Relationship to subagents

`/sudo` switches the current agent's persona in-place. It is cheaper than
spawning a subagent but provides no independent context isolation. For
independent review, spawn a subagent instead.
