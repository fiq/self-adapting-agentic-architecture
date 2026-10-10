# ADR-0006: Budget-aware harness optimisation is a control-plane responsibility

Date: 2026-10-04

## Status

Proposed.

This ADR fixes the architectural direction only. It does not authorise a learned
router, automatic escalation, trace ingestion, or changes to deterministic
promotion policy. Each behavioural slice still needs its own `CHG-*` proposal,
acceptance mapping and evidence.

## Context

SAAA already has the pieces of a harness-independent control plane:

- `AgentHarness` separates execution from deterministic approval;
- `AgentRoute` records the provider, model, tier and reason selected before an
  invocation;
- `ResourceBudget` carries token, credit, wall-clock and retry limits;
- `AgentUsage` records observed consumption after an invocation;
- retrieval, candidate isolation, checks, scoring, promotion and experiment
  history remain outside the model;
- `CHG-026` added populations and deterministic ranking on identical evidence.

`Q-010` records the unresolved question of resource-aware model routing. The
question is now broader than model selection. Model quality is only one input to
agent quality; context, tools, retrieval, workflow shape, verification and retry
policy can move substantial task difficulty out of the model and into the
harness.

That matters most when compute or money is constrained. A frontier model can
sometimes compensate for a weak general-purpose harness. A smaller, local or
cheaper model has less spare capability to waste rediscovering repository
structure, choosing among irrelevant tools, carrying unnecessary context or
inventing workflow discipline on every run.

The architectural question is therefore:

> Is SAAA another agent runtime, or is it the system that measures and improves
> the harnesses existing agents run inside?

The second answer fits the boundaries already present in the repository.

### Where this sits in ADR-0002

ADR-0002 describes three layers. This decision does not add a fourth, and it
does not change the boundary between them. It makes two of them more specific:

```text
ADR-0002 layer               what this ADR adds to it
---------------------------  ----------------------------------------------
Layer 1, evolve a workflow   the evolved object is named: a harness profile,
                             of which a workflow file is one part
Layer 2, SAAA as tooling     selecting a profile and escalating between
                             profiles is a primitive an outer loop can call
Layer 3, product code        nothing
```

ADR-0002's load-bearing rule, "the outer loop plans; the inner loop scores",
holds unchanged. Selecting and escalating between profiles is planning; it may
consume scores and may never override them.

### What is already recorded, and what is new

Some of this decision was already true before it was written down here:

- `Q-010` already requires routing to be deterministic, recorded before the
  proposal, chosen over explicit tiers and kept apart from candidate scoring.
- The README already says SAAA governs an agent and is not another
  general-purpose coding agent.

What this ADR adds is:

- the harness profile as the thing that is selected, compared and evolved;
- cheap-first selection with deliberate escalation;
- cost per successful task as the unit of cost;
- failure observations that say `UNKNOWN` when no cause was observed;
- learning offline before using any runtime evidence.

## Decision

### 1. SAAA is the harness control plane, not the agent data plane

Existing agents and runtimes execute work:

```text
Claude / Codex / OpenCode / Goose / LangGraph / custom agent
                         |
                         v
                    AgentHarness
                         |
                         v
                        SAAA
```

SAAA owns the evidence-backed control loop around them:

```text
observe -> evaluate -> experiment -> select -> route -> remember
```

SAAA does not grow a general-purpose graph runtime, tool runtime, chat runtime,
scheduler or distributed workflow engine merely to implement this direction.

This extends, rather than replaces, ADR-0003's smart-bridge direction. The
northbound surfaces may change; the authority boundary does not.

### 2. Make the selected harness a first-class, versioned concept

A later change should introduce a provider-neutral `HarnessProfile` (name
subject to the change review) representing the configuration SAAA selects,
compares and eventually adapts.

The conceptual shape is:

```text
HarnessProfile
|- model route
|- context policy
|- retrieval policy
|- tool policy
|- workflow policy
|- self-check policy
|- retry/escalation policy
`- resource limits
```

The self-check policy is what the agent does to test its own work inside an
attempt, such as running the tests before it answers. It is not the acceptance
check. The checks that decide whether an attempt succeeded, and every gate and
fitness rule, are fixed for the task before any profile is selected. No profile
may choose, remove, reorder or weaken them, and selecting a different profile
never changes what counts as success. Otherwise selection could buy a pass by
choosing an easier verifier, which is a gate change by another route.

The first delivered type must be smaller than this list. Do not model every
future harness dimension before one selection policy needs it. What matters now
is that profile identity and policy version are stable, auditable and independent
of provider implementation classes.

### 3. Prefer the cheapest eligible profile, then escalate deliberately

The runtime policy starts cheap and spends additional capability only when
recorded evidence justifies it.

Conceptually:

```text
small + minimal harness
          |
          +-- verified -> done
          |
          v
small + stronger harness
          |
          +-- verified -> done
          |
          v
balanced model
          |
          v
performance model
```

The model does not decide that it deserves a larger model, more budget or weaker
gates. A deterministic policy chooses the next eligible profile from declared
state and records why.

The first implementation must be a boring explicit policy over declared tiers
and budgets, not an opaque learned router.

Escalation needs something to escalate on. Cheap-first is only sound for a task
that declares a deterministic verifier: a check that can say, without a model,
whether an attempt succeeded. A task without one is not eligible. For such a
task escalation has no trigger, and the policy fails closed rather than guessing
whether the cheap attempt was good enough.

A verifier is necessary but not sufficient. Each attempt must also start from
the same clean state, in its own isolated worktree, with no external effects
that outlive it. Otherwise a failed cheap attempt can leave behind changes that
alter the next attempt's evidence, and the escalated result is no longer
comparable with the one it replaced.

### 4. Treat resource state as routing input, not merely an invocation limit

`ResourceBudget` already expresses the right dimensions. Selection and
escalation may consume:

- remaining input/output token allowance;
- remaining provider credits;
- remaining wall-clock budget;
- retry allowance;
- declared provider or local-runtime availability, which is not a
  `ResourceBudget` dimension and would be declared alongside it.

Every decision is recorded before invocation. Missing usage or price evidence is
recorded as unavailable rather than invented.

`AgentUsage` is to be accumulated across attempts, so that the useful economic
unit becomes **cost per successful task**, not merely tokens per call. Nothing
accumulates it today; slice 0 is where it starts being recorded.

### 5. Keep quality, cost and latency as separate evidence

SAAA must not hide the trade-off inside one unexplained scalar.

The eventual selection question is one of these shapes:

```text
cheapest profile where quality >= required floor
```

or:

```text
best observed quality where cost <= declared budget
```

Candidate fitness and promotion remain governed by their own deterministic
contracts. Harness selection may use fitness evidence, but it cannot rewrite the
fitness function after seeing a candidate.

A Pareto-front or other multi-objective selector is a later implementation
choice, not part of this ADR.

### 6. Harness adaptation should be failure-directed

A scalar result tells us which candidate won. It does not tell us what the
harness should change next.

Future slices should introduce bounded failure observations such as tool
selection, malformed tool arguments, missing context, poor retrieval, planning,
verification, looping, timeout, budget exhaustion and policy violation.

Where deterministic evidence cannot support a cause, record `UNKNOWN`. Never
manufacture diagnostic certainty with a model and then treat it as measured
fact.

These observations can later select bounded adaptation operators over context,
tools, retrieval, workflow and self-checks, never over the acceptance checks
fixed for the task.

### 7. Optimising small models means compiling repeated reasoning into the harness

A repeated expensive reasoning pattern is a candidate for deterministic or
retrievable infrastructure.

For example:

```text
large model repeatedly:
  inspect package structure
  locate implementation
  locate tests
  rediscover conventions
  implement
  verify

can become:
  bounded repo map
  relevant-symbol retrieval
  test lookup
  explicit workflow
  deterministic verifier
```

This is not a claim that harnesses can replace missing model capability. It is a
claim that a model should not repeatedly spend capability on work the system can
supply more cheaply and reliably.

### 8. Learn offline first; use runtime evidence later

The near-term optimisation loop remains isolated and reproducible:

```text
candidate profile
      |
      v
fixed task/evidence corpus
      |
      v
real execution
      |
      v
deterministic observations
      |
      v
retain / reject / compare
```

Production or developer traces can later become an input corpus, but trace
collection does not gain approval authority. OpenTelemetry support, shadow
experiments and champion/challenger routing are deferred until the local
selection and escalation contracts are proven.

## Delivery sequence

Do not implement this programme as one branch. Allocate `CHG-*` ids when each
slice is actually proposed.

### Slice 0 - the prerequisite for real cost: measured usage from a live proposer

Cost per successful task cannot be measured today, for two reasons:

- `AgentUsage` is returned by the harness adapters but is written to neither
  the journal nor the experiment ledger;
- no live model proposer fills a generation. The variety a generation ranks
  comes from the fixture proposer, so there is no real cost to measure.

The first slice is therefore the one ADR-0002 already names as Layer 1's gap: a
live proposer filling a generation, with each candidate's `AgentUsage`
recorded beside its evidence, together with the provider and model price
identity it was incurred under. Where usage or a price is unavailable, it is
recorded as unavailable, never as zero and never estimated; cost is only
comparable between attempts whose price identity is known. `Q-013`, which asks how variants in a generation
are identified when the proposer chooses its own mutation ids, has to be settled
as part of it.

Slices A to C can be built and tested before this exists, using declared tiers,
explicit task facts and controlled `AgentUsage` through the `AgentHarness` port.
What cannot happen without it is any empirical claim about real cost: no slice
may report cost per successful task, or compare profiles on cost, until slice 0
supplies measured usage and price identity.

### Slice A - harness-profile contract

Introduce the smallest provider-neutral profile identity needed to describe two
explicit alternatives. Reuse `AgentRoute`, `ResourceBudget` and existing
retrieval concepts instead of duplicating them.

Evidence: domain invariants, stable/versioned identity and architecture boundary
checks.

### Slice B - deterministic profile selection

Given explicit task characteristics, budget and a set of profiles, select one
before invocation and record the policy version, available budget, selected
profile and reason.

The tasks are drawn from a fixed evaluation corpus, run in isolated candidate
worktrees, as every SAAA run is today. Selection for live work outside the
corpus is out of scope for this slice.

No learned classifier. Task characteristics may initially be explicit input or
cheap deterministic facts.

### Slice C - progressive escalation and cumulative usage

Run a selected profile, consume `AgentUsage`, apply deterministic verification,
and escalate to the next declared profile only when evidence and remaining
budget permit it.

Prove that verified success does not escalate, that exhausted budgets fail
closed, and that a task with no declared verifier is refused rather than
escalated.

This slice runs over the offline corpus only. Escalating live, per task, for
work SAAA did not set up itself is task execution, and that is close to the
agent runtime this ADR rejects. It needs its own decision, made once slices C
and F have evidence, rather than arriving as an extension of this one.

### Slice D - failure observations and bounded adaptation operators

Introduce a small failure taxonomy and explicit harness adaptation operators.
Map only failures backed by observable evidence. Use `UNKNOWN` when no cause is
proven.

This is where evolution becomes targeted rather than blind variation.

### Slice E - trace/eval corpus ingestion

Define a provider-neutral run/trajectory envelope and import historical tasks as
an evaluation corpus. Prefer OpenTelemetry-compatible concepts, but do not make
an observability vendor part of the deterministic core.

### Slice F - cost/quality policy and export

Compare profiles on successful-task cost, quality and latency; select under a
quality floor or cost ceiling; and export the selected profile to supported
agent/harness formats where a concrete consumer exists.

Only after this slice has evidence should learned routing, shadow optimisation,
champion/challenger operation or automatic operator selection be considered.

## Non-goals

This ADR does not authorise:

- a LangGraph replacement or new general-purpose agent runtime;
- automatic production deployment or merge;
- an opaque ML model router;
- model-controlled budget increases or self-escalation;
- changing deterministic hard gates or promotion thresholds at runtime;
- distributed candidate execution;
- AST-aware mutation;
- automatic OpenTelemetry ingestion;
- live, per-task escalation for work outside the evaluation corpus;
- MAP-Elites, NSGA-II or another evolutionary algorithm merely because the
  programme can eventually use one.

## Consequences

### Benefits

- The project's product direction becomes clearer: optimise the system around
  models rather than compete with the models themselves.
- Existing `AgentHarness`, budget, route and usage types become parts of one
  coherent capability rather than isolated plumbing.
- Smaller, local and lower-cost models become intentional targets rather than
  fallback providers.
- Cost becomes measurable against successful outcomes rather than treated as a
  provider price-table concern.
- The evolutionary loop gets a concrete, economically useful target: harness
  policy.

### Costs and risks

- A profile abstraction can become a configuration dumping ground. Each slice
  must add only dimensions it can measure.
- Cheap-first routing can create false economy if it causes repeated failed
  attempts. Cumulative cost and latency must include escalation, not only the
  winning call.
- Failure classification can become model-authored storytelling. Only observed
  evidence may steer automatic adaptation.
- Task-specific harnesses can overfit. Held-out tasks and cross-model transfer
  should become evaluation requirements before an adapted profile is called
  generally useful.
- Provider pricing and usage metadata change. Price identity and policy version
  must be part of comparable evidence wherever cost is used.

## Relationship to open questions

`Q-010` remains open, but its role changes. This ADR settles **where routing
belongs and what must remain deterministic**. Q-010 now tracks the empirical
questions: which task characteristics discriminate, what price/usage evidence
is trustworthy, and whether cheap-first selection improves cost per successful
task without quality regression.

`Q-011` remains open. Ethical and preference-aware routing is an eligibility
layer over the same profile set: hard provider/locality/data constraints filter
routes before cost optimisation; soft preferences may rank eligible routes but
must remain explicit and auditable.

## Review conditions

Revisit this decision if any of the following is observed:

- cheap-first escalation costs more per successful task than the fixed strong
  model it replaces;
- routing quality regresses against a representative held-out corpus;
- harness profiles become too provider-specific to remain meaningful at the
  deterministic boundary;
- the required execution control cannot be expressed through `AgentHarness`
  without turning SAAA into the runtime this ADR explicitly rejects;
- repeated experiments show harness adaptation does not materially improve the
  useful range of lower-cost models;
- users primarily value SAAA as a candidate evaluator and not as a harness
  optimiser, making the additional control-plane policy unjustified.

## Related evidence

- `ADR-0002` - three-layer product direction and deterministic inner authority.
- `ADR-0003` - smart-bridge northbound interfaces.
- `ARCH-001` - model/provider code cannot own validation, scoring or promotion.
- `Q-010` - resource-aware routing evidence still required.
- `Q-011` - ethics and preference constraints remain a separate eligibility
  policy.
- `specs/changes/CHG-006-agent-harness-boundary`, `CHG-009`, `CHG-010` and
  `CHG-012` - the harness, external-agent and bounded-resource seams this
  decision builds on. The first is cited by path because two change directories
  share the id `CHG-006`.
- `Q-013` - variant identity in a generation, which slice 0 has to settle.
