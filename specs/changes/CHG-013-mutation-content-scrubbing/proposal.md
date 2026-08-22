# CHG-013: Scrub mutation content at the durable and user-visible boundaries

## Why

`ProposerEvidenceSanitizer` was added to scrub prompt and raw-provider evidence
before it is committed into a target repository. It is applied only to the
proposer-evidence block. The mutation's own `summary` and `patch` are written
verbatim into `.saaa/candidates/<id>.toon` and printed to the console by
`ConsoleReporter` before validation runs.

Proposers execute with `SAAA_MODEL_API_KEY` in their environment, so a model or
external agent can place a credential into mutation text. Today that text is
printed and durably committed without redaction.

## What

Move scrubbing from a single call site to the boundaries where content becomes
durable or user-visible, so that every current and future entrypoint inherits
it. `EvolveCommand`, the `saaa_evolve` MCP tool and `saaa sa` are all affected
equally and must not each carry their own copy.

## Not this change

- widening the sanitizer's pattern set beyond the recorded API-key and bearer
  forms, which needs its own evidence;
- sandboxing the proposer process itself;
- changing what a proposer is permitted to read.

## Relates to

RISK-006, ARCH-001, CON-002. Identified during the independent review of PR #29
and deliberately scoped out of that branch because it predates it.
