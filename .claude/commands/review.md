# /review

Run a brief, bounded clean-up review of the current diff: boy-scout cleanup,
code and architectural smells, and inappropriate coupling. Enforces the
standing quality rule; it does not hunt for correctness bugs.

## Usage

```
/review
```

- Loads the matching `specialise/runtime-*` "Language smells" section for the
  project's language.
- Applies safe cleanups with tests green; records residual findings as
  `RISK-`/`PAT-` knowledge or a follow-up change, never a silent TODO.
- Caps at two passes; escalates genuine design disagreement to
  adversarial-debate with per-persona stance.

See `.agents/skills/workflow/review-loop/SKILL.md` for full guidance.
