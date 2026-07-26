# Architecture

self-adapting-agentic-architecture is a local Java CLI with Clean Architecture
module boundaries.

```text
cli -> application -> core
           ^
           |
  adapters and benchmarks implement ports
```

The key architecture rule is `ARCH-001`: LangChain4j is an adapter detail, and
validation, fitness scoring, promotion and rollback remain deterministic Java
decisions. `RISK-001` tracks the model self-approval failure mode.

Run `.agentic-template/bin/project lint` to enforce the first boundary fitness
function.
