# Mutation Fitness Loop

## Why

The project needs a first executable slice that proves workflow evolution is
auditable and reversible. The loop should show that a model can propose a
bounded mutation while deterministic code owns validation, candidate isolation,
fitness scoring and promotion or discard.

## Intent

Create the smallest Java architecture and first outside-in acceptance test for
one candidate mutation being evaluated and discarded or promoted.

## Non-goals

- No OpenSearch or vector storage.
- No AST mutation.
- No LSP integration.
- No distributed workers.
- No automatic production deployment.
- No model authority over validation, scoring, promotion or rollback.
