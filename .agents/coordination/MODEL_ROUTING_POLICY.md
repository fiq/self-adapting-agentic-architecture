# Model Routing and Session Efficiency Policy

Route work by task complexity, uncertainty, impact and reversibility. Use the
cheapest model class likely to complete a bounded task reliably. This policy
governs agent operation; it does not give a model authority over SAAA's
deterministic validation, fitness, promotion or rollback.

## Defaults

Claude:
- persistent team coordination
- architecture synthesis
- product reasoning
- cross-cutting system design
- sustained context

Codex:
- bounded implementation
- repo inspection
- agreed spec implementation
- engineering red-team review
- independent code review
- build and CI repair

Other OpenAI-compatible or large-context models:
- broad corpus analysis
- contradiction detection
- option-space generation
- independent second opinion

Direct user routing wins. Do not secretly reroute a directly addressed task.

## Model Classes

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

Escalate when assumptions conflict, knowledge is missing, architecture
boundaries are unclear, tests repeatedly fail, security or privacy risk is
material, a public contract changes, the task cannot be safely decomposed, or
reviewers disagree.

## Session reuse and reset

A model session is disposable execution state. The repository, structured
specification and `HANDOFF.toon` are the durable source of truth.

- Reuse one session for one stable objective, role and data-permission scope;
  retain its session ID in the handoff or task record. This allows provider
  prompt caching and avoids repeatedly sending the same context.
- Start a fresh session when the objective, delegated role, source-access or
  privacy/credential scope changes; when an independent review begins; after a
  transport/terminal-response failure; or when keeping the transcript would
  consume the configured response reserve.
- Do not equate a longer transcript with better context. Compact the durable
  state into a source-referenced context packet before handoff or reset.
- A cached session is never a substitute for rereading changed source, tests or
  policy; it only avoids resending stable context.

## Context and budget discipline

Before a non-trivial model call, define the smallest useful budget envelope:
input tokens, output tokens, provider credits or price identity, wall-clock
limit and retry allowance. Record actual input/output tokens, cache-hit or
cache-read evidence, latency and cost when the provider exposes them. Record
`unavailable` rather than inventing a price or cache ratio.

- Start with a semantic summary and source references, then attach only the
  bounded diff or files necessary for the task. Never send the entire
  repository by default.
- Reserve at least the profile's response budget for synthesis and handoff.
  Summarise or reset before that reserve is exhausted.
- Use a lower-cost model for mechanical work and deterministic tooling first;
  escalate only for ambiguity, consequential trade-offs, repeated failure or a
  required independent challenge.
- Treat provider cache metrics as efficiency telemetry, not as quality,
  safety, correctness or fitness evidence.

## Multi-model protocol

Every model invocation has one explicit role: lead, bounded implementer,
specialist analyst, independent reviewer or mechanical maintainer. The lead
owns synthesis and records why a model class was selected.

1. Give each delegated role a bounded context packet: objective, acceptance
   criteria, relevant source references, constraints, budget and expected
   output. Do not delegate an opaque transcript.
2. Use a fresh reviewer session and a focused diff/packet for independent
   review. The reviewer must not inherit an implementer's unreviewed verdict.
3. After two bounded failures or an exhausted retry budget, stop blind retrying;
   record the evidence, compact the context and escalate to the next suitable
   role or request human direction.
4. A direct user model/provider choice wins. Otherwise the role and explicit
   budget—not a model-name heuristic—select the route.

## Engine routing defaults

Defaults, not automatic routing. A human or lead agent chooses; nothing in SAAA
selects an engine at runtime. Automatic provider routing stays deferred behind
`Q-010`'s measured usage and price-identity evidence and `Q-011`'s preference
constraints, and nothing here changes that.

These are `proposed`. They come from one day's observed use on 2026-08-22, not
from measurement.

| Work | Default | Why |
|---|---|---|
| Review, audit, read-heavy investigation | external engine on a separate budget | reviews are read-only and fan out safely; keeping them off the lead's budget is what makes running several affordable |
| Generating candidates a deterministic check then verifies | external engine | cheap to generate, deterministic to check. The asymmetry is the point |
| Bounded implementation with clear acceptance criteria | subagent in its own worktree | needs judgement but not the whole conversation; isolation prevents file collisions |
| Work needing the current conversation's context | fork of the lead | inheriting context avoids re-deriving it, which is the expensive failure mode |
| Spec authorship, merge decisions, anything touching the deterministic boundary | the lead | these must be defensible, and the lead is accountable for them |

Cost shapes the default but does not decide it. Cached prefix tokens are roughly
an order of magnitude cheaper than fresh input on the providers observed, so
reusing a retained session for follow-up work in the same scope is materially
cheaper than starting one. That is a reason to keep a session, never a reason to
keep an independent reviewer's session — see `PAT-002`.

Prefer the cheapest model class that will reliably complete the bounded task, and
prefer a reasoning-capable variant when it costs the same. Reviews and design
critique are the tasks most sensitive to model strength.

Revisit when: observed cost or capability makes a default wrong; a provider
changes tiers or prices; a measured comparison contradicts one of these rows; or
`Q-010` and `Q-011` are answered and automatic routing becomes possible, at which
point this table becomes input to that decision rather than the decision itself.

## Audit and safety invariants

For material work, record the route/model identity, session ID or reset reason,
budget envelope, observed usage, cache evidence, outcome and handoff source
references in `HANDOFF.toon`. Never place credentials, raw provider secrets or
unbounded provider transcripts in the record.

SAAA does not yet automatically select providers by complexity, remaining
credits or cache ratio. Any future automatic routing needs the measured usage,
price identity, deterministic decision record, failure fallback and ablation
evidence required by `Q-010`; ethical/provider constraints remain governed by
`Q-011`. No route selection can alter `ARCH-001` or mitigate `RISK-001` by
letting a model approve its own candidate.
