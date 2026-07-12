---
name: model-routing
description: Route work to appropriate agents and models with token efficiency and a handoff protocol.
---

# Model Routing

Route by task complexity, uncertainty, impact and reversibility. Use the
cheapest model class likely to complete the bounded task reliably.

## Model classes

Strong model:

- ambiguity resolution
- architecture synthesis
- risk assessment
- conflict resolution
- high-impact final review

Midrange model:

- bounded planning
- implementation
- test creation
- documentation
- routine review

Lesser or local model:

- mechanical edits
- narrow transformations
- test execution
- indexing
- metadata extraction
- knowledge-link maintenance

Direct user addressing wins. The addressed agent may delegate bounded support
while retaining responsibility.

## Handoff protocol

Before changing model or provider:

1. update `HANDOFF.toon`;
2. record fixed decisions;
3. record unresolved ambiguity;
4. preserve test results;
5. record branch, worktree and commit state;
6. identify safe bounded next work.

## When to escalate

Escalate when:

- architecture assumptions conflict;
- public contracts change;
- tests repeatedly fail;
- security or privacy risk appears;
- reviewers disagree;
- the task can no longer be safely bounded.

## Do not

- Secretly reroute a directly addressed task.
- Switch models mid-task without recording handoff state.
- Use a strong model for mechanical edits when a cheaper model is reliable.
