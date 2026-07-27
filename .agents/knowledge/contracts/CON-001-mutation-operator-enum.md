---
id: CON-001
type: contract
title: Mutation operator enum
status: proposed
summary: The mutation IR uses a closed initial operator enum so the next loop can derive bounds, evidence and fitness defaults deterministically.
owners:
  - architect
relates_to:
  - SYS-001
  - ARCH-001
  - Q-002
  - Q-005
risks:
  - RISK-001
evidence:
  - docs/architecture/evolutionary-operators.md
  - specs/changes/CHG-002-live-loop-policy/design.md
review_after: 2026-10-26
---

# Mutation Operator Enum

The first mutation IR has a closed operator enum:

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

The enum is semi-declarative input into the next loop. It selects deterministic
defaults for target validation, bounds, required evidence and fitness
dimensions. Unknown operators are rejected until a proposal defines their
semantics and validation path.

`hill-climb` exploits a local fitness gradient near an evaluated parent.
`exploratory-leap` tests a larger moonshot technique under an explicit risk
budget. Both remain deterministic-selection operators; neither lets model
output approve itself.

Conceptual crossover is not a mutation operator enum value. It is a deferred
recombination policy that produces a child mutation contract using one of the
closed operator values above.
