# Architecture

self-adapting-agentic-architecture is a local Java CLI with Clean Architecture
module boundaries.

All Java lives under `modules/`, and layers are named for what they may know.

```text
cli -> deterministic -> domain
             ^
             |
  adapters and benchmarks implement ports
```

`domain` declares no dependencies at all, so the inward rule is a compile error.
`deterministic` holds validation, scoring, promotion and ports; nothing
provider-aware or nondeterministic belongs there.

The key architecture rule is `ARCH-001`: LangChain4j is an adapter detail, and
validation, fitness scoring, promotion and rollback remain deterministic Java
decisions. `RISK-001` tracks the model self-approval failure mode.

Evolutionary operator policy is captured in
[`docs/architecture/evolutionary-operators.md`](../architecture/evolutionary-operators.md):
mutation is a targeted behavioral variation, not a patch; the realization is
the candidate Git diff; crossover starts as conceptual trait recombination over
evaluated parents, not raw diff merging. `CON-001` defines the initial mutation
operator enum, including `hill-climb` and `exploratory-leap` search posture
operators. `Q-002`, `Q-004`, `Q-005` and `Q-006` track the open contract,
fitness, crossover and search posture details.

Run `.agentic-template/bin/project lint` to enforce the first boundary fitness
function.
