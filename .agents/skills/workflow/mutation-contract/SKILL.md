---
name: mutation-contract
description: Convert human or LLM freeform mutation/crossover ideas into bounded behavior-first mutation contracts for the self-adapting agentic architecture loop.
---

# Mutation Contract

Use this when input says to mutate, evolve, improve, repair, crossover or
combine candidate ideas.

## Rules

- Treat the input as a proposal, not approval.
- Model mutation as behavioral variation, not a patch.
- Emit a TOON audit envelope and a canonical S-expression mutation IR.
- Keep one primary target locus unless the user explicitly approves broader
  scope.
- Prefer targeted mutation before crossover.
- For crossover, combine concepts, techniques or evidence-backed traits; do not
  merge raw diffs.
- Preserve deterministic validation, fitness scoring, promotion and rollback.

## Workflow

1. Extract the target: method, class, workflow node, policy, config or unknown.
2. Extract the hypothesis: what behavior should improve and why.
3. Select an operator from the closed initial enum:
   - `targeted-behavior-change`
   - `repair`
   - `simplify`
   - `performance-tune`
   - `guardrail-change`
   - `tool-strategy-change`
   - `model-routing-change`
   - `prompt-policy-change`
   - `hill-climb`
   - `exploratory-leap`
4. Set bounds: files, lines, public API, persistence, config, network and
   safety constraints.
5. Define required evidence: unit, property, integration, benchmark, trace or
   reviewer evidence.
6. Define fitness gates and objectives.
7. Canonicalize the internal S-expression: stable field order, lower-kebab
   atoms and quoted free text.
8. If material information is missing, ask the smallest question that prevents
   an unsafe or unauditable contract.

## Output Shape

```toon
mutation:
  id: MUT-unknown
  operator: targeted-behavior-change
  hypothesis: one sentence
  target:
    kind: method
    file: unknown
    symbol: unknown
  bounds:
    max_files_changed: 2
    max_lines_changed: 80
    public_api_change: false
    persistence_change: false
  required_evidence:
    - unit_tests_pass
    - property_tests_pass
  fitness:
    hard_gates:
      - deterministic_checks_pass
      - required_evidence_present

internal:
  sexpr: (mutation (id MUT-unknown) (operator targeted-behavior-change) (target (kind method) (file unknown) (symbol unknown)) (bounds (max-files-changed 2) (max-lines-changed 80) (public-api-change false) (persistence-change false)) (evidence unit-tests-pass property-tests-pass))
```

For crossover:

```toon
recombination:
  type: conceptual-crossover
  parents:
    - candidate: parent-a
      trait: evidence-backed trait
    - candidate: parent-b
      trait: evidence-backed trait

mutation:
  operator: targeted-behavior-change
  child_hypothesis: one sentence
  target:
    kind: method
    file: unknown
    symbol: unknown

internal:
  sexpr: (mutation (operator targeted-behavior-change) (parents (parent parent-a (trait "evidence-backed trait")) (parent parent-b (trait "evidence-backed trait"))) (target (kind method) (file unknown) (symbol unknown)))
```

For search posture:

```toon
mutation:
  operator: hill-climb
  parent: candidate-042
  objective: cost_latency_budget
  target:
    kind: method
    file: unknown
    symbol: unknown

internal:
  sexpr: (mutation (operator hill-climb) (parent candidate-042) (objective cost-latency-budget) (target (kind method) (file unknown) (symbol unknown)))
```
