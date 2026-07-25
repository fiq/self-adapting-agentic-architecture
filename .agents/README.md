# Agent System

This directory holds project-local agent topology, coordination policies and
lazy-loaded skills. `AGENTS.md` remains canonical.

Load only the files needed for the current task.

## Knowledge Layer

Use `.agents/knowledge/` for durable reviewed project knowledge and
`.agents/knowledge/inbox/` for unreviewed proposals. Keep `HANDOFF.toon`
limited to active task state and reference knowledge IDs instead of copying
entries.

```
request -> team selection -> knowledge search -> optional Superpowers workflow
        -> implementation/review -> handoff -> knowledge capture
        -> inbox proposal -> review -> canonical knowledge
```

When Superpowers is available, it owns engineering workflow steps such as
brainstorming, planning, TDD, debugging, implementation, review and
verification. This repository owns local roles, knowledge retrieval, learning
capture and promotion rules, and must also work without Superpowers.

## Context Packaging

Use `tooling/context-packet` when delegating, handing off under context
pressure, or asking another model for review. Send semantic summaries, source
refs and only necessary snippets; do not send opaque encoded context.
