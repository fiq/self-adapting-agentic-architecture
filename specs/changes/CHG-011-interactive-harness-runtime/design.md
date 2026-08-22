# CHG-011 design

## State machine

```text
(start) --> ACTIVE --close/quit or EOF--> CLOSED
              |  ^
              |  |
              +-- inspect catalog / select target / select route
              |
              +-- evolve(HARNESS_WORKFLOW | CODE)  [synchronous; stays ACTIVE]
```

`HarnessSessionStatus` has exactly two values, `ACTIVE` and `CLOSED`. There is
no separate evaluating state: `evolve` runs the deterministic loop synchronously
and the session remains `ACTIVE` throughout, so a failed run reports an error and
returns to the prompt rather than changing state.

The state machine is deterministic and has no dependency on a provider,
terminal, MCP, or file system. The CLI owns line parsing and rendering. Adapter
composition owns the existing proposer profiles and `EvolveRunner`. This keeps
the session protocol testable without making presentation code an approval
boundary.

## Target policy

`HARNESS_WORKFLOW` and `CODE` both use the existing bounded whole-file path,
with a declared behaviour-case script as the hard gate. The session target kind
is explicit audit context for the operator; it does not claim a richer code
realizer than SAAA currently has. AST-aware or language-specific mutation needs
its own later policy and adapter slice.

## Routing policy

The catalog exposes only profiles already registered by SAAA (`fixture`,
`openai-compatible`, and `acp`). Selecting one is an operator choice and is
shown in status. It is not an automatic route decision and does not resolve
Q-010 or Q-011.

## Command protocol

The first commands are `help`, `status`, `capabilities`, `skills`, `target`,
`route`, `evolve`, and `quit`. Command errors leave an active session active.

`evolve` in this slice fixes the retrieval treatment to `NONE`, the diff budget
to 80 lines and the task text to a constant. `saaa-evolve` exposes `--retrieval`
and `--task`; the session deliberately does not, so a first interactive slice
cannot vary retrieval treatment. This is a recorded capability gap, not an
oversight, and it must be closed before the session is used to compare retrieval
modes.
`evolve` reports the deterministic result from the existing loop; an agent
proposal remains subject to the current validation, isolated candidate,
checks, scoring, and promotion-recording path.
