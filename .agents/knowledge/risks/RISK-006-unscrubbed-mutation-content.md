---
id: RISK-006
type: risk
title: Mutation summary and patch content bypass credential scrubbing
status: open
summary: Candidate TOON bookkeeping and console output write proposer-authored mutation summary and patch text verbatim, while ProposerEvidenceSanitizer covers only the proposer-evidence block, so a provider credential echoed into a mutation would be printed and committed unredacted.
owners:
  - architect
  - security-reviewer
relates_to:
  - ARCH-001
  - SYS-001
  - CON-002
decisions:
  - ADR-0003
evidence:
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspace.java
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/ProposerEvidenceSanitizer.java
  - modules/cli/src/main/java/com/dreamthought/saaa/cli/ConsoleReporter.java
  - https://github.com/fiq/self-adapting-agentic-architecture/pull/29#issuecomment-5378168554
review_after: 2026-09-30
---

# Unscrubbed Mutation Content

`GitCandidateWorkspace` renders a candidate's TOON bookkeeping from
`mutation.summary()` and `mutation.patch()` directly. Only `proposerBlock(...)`
passes through `ProposerEvidenceSanitizer`, so the scrubbing added for prompt
and raw-response evidence does not cover the mutation itself. `ConsoleReporter`
prints `mutation.summary()` before validation runs, so unredacted text reaches
the terminal even for a candidate that is later rejected.

A proposer executes with `SAAA_MODEL_API_KEY` available in its environment. A
model that echoes its own configuration, or an ACP agent that quotes a failing
request, can therefore place a credential into a mutation summary or patch,
where it is printed and then committed into the target repository's
`.saaa/candidates/<id>.toon`.

This is not specific to any one entrypoint. `EvolveCommand`, the `saaa_evolve`
MCP tool and `saaa sa` all compose the same `EvolveRunner` with the same
reporter, so all three share the exposure. It was identified during the
independent review of PR #29 and is explicitly **not** a regression introduced
by that branch.

Containment today is partial: behaviour-check subprocesses run with a scrubbed
environment allow-list, so a candidate's own checks cannot read the key, and the
MCP response scrubber covers the MCP transport specifically. Neither protects
console output or the durable candidate envelope.

The fix belongs at the durable and user-visible write boundaries rather than in
any single caller, so that a future entrypoint inherits it. Sequence it with
CHG-013; do not treat the `sa` slice as blocked on it.
