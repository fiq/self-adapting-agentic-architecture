---
id: ADR-0002
type: decision
title: Three-layer vision for SAAA, delivered as vertical slices
status: proposed
summary: SAAA is designed as three concentric layers — workflow evolution, tool surface for a custom agentic loop, and product-code generalisation — and delivered through vertical slices across all three layers, with population and crossover as foundation slices that upgrade every layer at once. The deterministic decision boundary (ARCH-001) holds at every layer.
owners:
  - architect
relates_to:
  - ARCH-001
  - SYS-001
  - CON-001
  - RISK-001
  - RISK-003
  - Q-005
evidence:
  - docs/decisions/0002-three-layer-vision.md
  - README.md
  - PROJECT_PROFILE.toon
  - specs/changes/CHG-003-first-vertical-slice/
review_after: 2026-12-31
---

# ADR-0002: Three-layer vision for SAAA

Details live in `docs/decisions/0002-three-layer-vision.md`. This knowledge
node exists so specs, wiki pages and other knowledge entries can link the
decision by id.

## Summary

Three concentric layers:

1. **Layer 1** — evolve a workflow, prompt or agent-configuration file.
   Shipped end-to-end for one candidate per run (`CHG-003`), with a canned
   fixture proposer.
2. **Layer 2** — SAAA exposed as a tool an outer agentic loop can call, so the
   outer loop plans *what to try* and consumes SAAA's scores rather than
   overriding them.
3. **Layer 3** — the same loop applied to product code, gated by existing
   tests and benchmarks rather than by workflow behaviour scripts.

Delivered as **vertical slices** across all three layers where possible, with
**population** and **conceptual crossover** as foundation slices that upgrade
every layer at once. The deterministic decision boundary in `ARCH-001` holds at
every layer: the model may propose or repair, never approve.

## Load-bearing rule

At Layer 2, the outer loop plans; the inner loop scores. An outer model
grading the inner scores is the original problem in a bigger box.
