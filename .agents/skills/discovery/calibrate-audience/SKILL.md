---
name: calibrate-audience
description: Establish audience skill level and app shape early, then right-size the architecture as a conscious choice.
---

# Calibrate Audience

## Purpose

Establish who the work is for and what shape it is, early and in plain
language, so the architecture is right-sized as a deliberate, recorded choice.

## Procedure

1. Infer from repository evidence first: existing runtime, entrypoints, UI,
   services and docs. Ask only the smallest set of questions evidence cannot
   answer.
2. Establish **skill level** on a simple scale: `normal_folk` (little or no
   coding background) to `developer`. This sets explanation depth and jargon.
3. Establish **app shape** in normal-folk terms:
   - browser-only (runs entirely in the visitor's browser, nothing to run on a
     server);
   - full-stack (a browser part plus a server part that stores or processes
     data);
   - service, CLI, library, mobile, desktop or game as evidence indicates.
4. Recommend **language by role**, not fashion: match the runtime already
   present, the team's stated skill, and the app shape.
5. Produce an explicit **right-sizing recommendation**: the smallest sufficient
   architecture, what is deliberately excluded and why, and the conditions that
   would justify revisiting it.

## Output

Record to `PROJECT_PROFILE.toon`:

```yaml
audience:
  skill_level: normal_folk | developer
  app_shape: browser_only | full_stack | service | cli | library | mobile | desktop | game
  language_by_role: []
  right_sizing:
    smallest_sufficient: one line
    deliberately_excluded: []
    revisit_when: []
    bought_in: true
```

## Rules

- Right-sizing is never silent. State exclusions, get buy-in, and record the
  decision (ADR when durable) before proceeding.
- Explain full-stack versus browser-only for a non-technical audience.
- Prefer evidence over questions; prefer the smallest useful question set.
- Re-run when audience or app shape changes materially.
