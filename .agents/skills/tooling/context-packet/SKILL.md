---
name: context-packet
description: Package bounded context for another agent or model while respecting context windows and preserving source fidelity.
---

# Context Packet

## Purpose

Give another agent enough context to complete a bounded task without flooding
the context window or hiding source evidence.

## Use when

- delegating to a subagent or another model;
- handing off under context pressure;
- asking for review, critique or second opinion;
- summarising repository evidence for long-running work.

## Budget

Use `PROJECT_PROFILE.toon.tooling.context_budget`. If the target is unknown,
assume `small` and reserve at least 30% of the window for the receiver's answer.

| Target | Include |
|---|---|
| `small` | objective, requested output, acceptance, non-goals, risks, 5-10 refs |
| `medium` | small + changed-file summary and short key snippets |
| `large` | medium + alternatives, discarded options and fuller evidence trail |

## Packet shape

```toon
context_packet:
  objective: one sentence
  requested_output: exact deliverable
  acceptance:
    - observable condition
  non_goals:
    - excluded work
  facts:
    - claim with source ref
  assumptions:
    - assumption and validation path
  decisions:
    - fixed decision
  risks:
    - risk and why it matters
  files:
    - path: path/to/file
      reason: why receiver may need it
  snippets:
    - ref: path/to/file:line
      purpose: why this excerpt is included
      content: short excerpt only
  knowledge:
    consulted:
      - ID-or-path
    open_questions: []
  ask_before:
    - destructive action
```

## Rules

- Summarise meaning, not bytes.
- Prefer summaries, IDs, file refs, line refs and hashes.
- Include exact snippets only when exact wording or code shape matters.
- Prefer “read `path:line` if touching X” over pasting whole files.
- Split work when a packet would exceed the configured target.
- Do not encode semantic context into opaque transport blobs.
- Do not resend unchanged context already available to the receiver.
