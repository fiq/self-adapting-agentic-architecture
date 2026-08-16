# CHG-012: Bounded model-session efficiency

## Why

SAAA is becoming a control plane around interchangeable model and agent
adapters. Repeating broad context in short-lived provider sessions wastes
tokens, while unbounded retained sessions create stale-context, independence
and audit risks. The project needs one operational rule set for efficient
session reuse and multi-model role allocation before implementing automatic
runtime routing.

## Intent

Codify a source-referenced, budget-aware operating policy for agent sessions
and multi-model work. It makes reuse, reset, escalation, reviewer independence
and usage recording observable repository practice, without changing the
deterministic mutation-evaluation authority.

## Non-goals

- automatic provider/model selection, retry or fallback in the SAAA runtime;
- an assumed provider price schedule, cache metric or cost estimate;
- retaining provider transcripts as repository memory;
- changing validation, fitness, promotion, rollback or privacy constraints.
