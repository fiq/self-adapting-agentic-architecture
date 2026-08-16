# CHG-011: Interactive harness runtime

## Why

SAAA can invoke interchangeable proposal engines, but its only human-facing
entrypoint is a one-shot evolution command. A developer cannot inspect the
available capabilities, select an explicit evolution target and route, or keep
that bounded context while running more than one governed action.

## Intent

Add `saaa sa`, an in-process interactive session client owned by SAAA. The
session presents a small catalog of capabilities and skills, records the
selected target and existing proposer profile, and dispatches an evolution
request through the current SAAA loop. It is a control-plane UX, not a new
agent authority.

## First-slice boundary

The first slice uses line-oriented commands so it is scriptable and acceptance
testable without a terminal dependency. It models both `CODE` and
`HARNESS_WORKFLOW` as explicit session targets. Both dispatch through the
existing bounded whole-file realization path and declared behaviour checks.
The code path is therefore deliberately partial: it does not claim AST-aware
editing, language-server analysis, or a general code-mutation policy.

`sa` is the session client name within the existing `saaa` binary (`saaa sa`).
Creating a second generic `sa` executable is deliberately out of scope because
the current public-command namespace is `saaa-*`.

## Non-goals

- replacing MCP, which remains the agent-host integration surface;
- automatic complexity, budget, or ethical route selection;
- a chat protocol, terminal UI library, durable multi-process sessions, or a
  network service;
- treating an agent result as a score, promotion, or merge decision;
- AST-aware or language-specific code mutation.
