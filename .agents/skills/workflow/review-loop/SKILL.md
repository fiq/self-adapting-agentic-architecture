---
name: review-loop
description: Brief cyclic clean-up review — boy-scout, code/architectural smells and inappropriate coupling.
---

# Review Loop

## Outcome

A short, bounded clean-up pass that leaves changed code cleaner (boy-scout
rule) and surfaces smells and coupling before merge. Enforces the standing
quality rule; it does not hunt for correctness bugs (use code review for that).

## Loop

```
diff ──► pass 1: smells + coupling ──► apply safe cleanups (tests green)
     └─► pass 2: re-check ──► record residual findings ──► stop (≤2 passes)
```

## What to look for

```
boy-scout        dead code, stale TODOs, unclear names in the change's path
code smells      long method, large class, duplication, feature envy,
                 primitive obsession, shotgun surgery
language smells  load the matching specialise/runtime-* "Language smells"
                 section lazily for the project's language
architectural    dependency cycles, layering violations, god modules,
                 leaky abstractions (architect persona)
coupling         inappropriate coupling; wrong dependency direction; a change
                 that ripples across many modules
```

## Rules

- Reuse over duplication: extract shared utility at the 2nd+ occurrence, never
  pre-abstract on one.
- Refactor only with tests green; keep changes within the diff's scope.
- Record material findings as `RISK-`/`PAT-` knowledge or a follow-up change —
  never leave a silent TODO.
- Cap at two passes; escalate a genuine design disagreement to
  `adversarial-debate` with per-persona stance.

## Do not

- Expand scope into unrelated refactoring.
- Claim clean-up without tests green.
- Duplicate correctness review.
