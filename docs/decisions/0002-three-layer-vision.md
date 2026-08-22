# ADR-0002: Three-layer vision for SAAA

## Status

Accepted (merged 2026-07-31 as `8456a19` via PR #3)

## Context

The first vertical slice (`CHG-003`) evolves a workflow definition file with a
fixture proposer and one candidate per run. That is enough to prove the pipe
end-to-end, but it does not, on its own, explain what SAAA is *for*. Without a
stated direction the README either overclaims (implying agentic and product-code
capability that does not exist) or underclaims (looks like a niche
prompt-evolution toy).

We need a durable statement of the direction so that:

- the README can link to a single source and stop drifting;
- the next slices (population, live proposer, tool surface) have a clear place
  to land;
- the boundary that keeps the deciding step deterministic is stated once at each
  layer, rather than re-derived every time the scope widens.

## Decision

SAAA is designed as three concentric layers. Each one extends the reach of the
previous one without weakening the deterministic decision boundary
(`ARCH-001`).

### Layer 1 — evolve a workflow

The mutation target is a workflow, prompt, tool policy, guardrail set or similar
piece of *agent configuration*. Behaviour cases are shell scripts you write. A
proposer suggests a bounded change; SAAA realizes it in a Git worktree, runs the
scripts, scores deterministically, decides `PROMOTE` or `DISCARD`, and appends
to a journal.

*What's shipped:* the whole loop end-to-end for one candidate per run, with a
canned fixture proposer.

*What's missing:* a live model proposer wired through the LangChain4j adapter,
and several candidates per generation with ranking between them (**the
population slice**).

### Layer 2 — SAAA as tooling for a custom agentic loop

The same engine, exposed as a tool an outer agent can call. Likely surfaces:
CLI (already the case), an MCP server, a small SDK. The outer loop plans *what
to try* — which file to mutate, what the hypothesis is, what new behaviour case
to add from a bug report or a spec, when to stop — and calls SAAA as a scored
`propose N candidates → evaluate → return ranked survivors` primitive.

**The load-bearing rule:** the outer loop plans; the inner loop scores. The
outer loop consumes SAAA's scores; it does not override them. Otherwise the
determinism benefit is reintroduced-then-lost one level up — an outer model
grading the inner scores is the original problem in a bigger box.

*What's shipped:* the CLI already qualifies as a tool surface. Nothing else.

*What's missing:* the population slice (so there is something worth ranking),
then MCP or SDK exposure, then a small set of adjacent tools (list runs, read
journal, propose a behaviour case from a diff, materialize a promoted
candidate).

### Layer 3 — generalise to product code

The mutation target becomes application code in a feature branch. Behaviour
cases become the existing test suite, contract tests, benchmarks, acceptance
suites. Realization eventually needs hunks or AST-aware transformations rather
than whole-file text replacement.

*Where this is honest:* bug fix with a failing test, refactor under contract
tests, performance work under a benchmark. The `.sh` behaviour case translates
directly and the score means something.

*Where this stops being honest:* changes graded on taste, review conversations,
API ergonomics, or long-lived architectural fit. The fitness function stops
carrying the weight and the outer loop (Layer 2) has to do its own thing there
rather than pretending SAAA solved it.

*What's shipped:* nothing at this layer.

*What's missing:* everything above, plus AST-aware realization
(`PROJECT_PROFILE.toon` records AST mutation as deferred with a revisit trigger
of "targeted contracts repeatedly fail deterministic validation because
code-aware structure is required" — this is that trigger).

## Delivery pattern: vertical slices, with a few foundation slices

`CHG-003` was already vertical *within* Layer 1 — CLI, Git worktree, scoring
and journal all shipped together. Rather than climb the layers sequentially,
we extend that vertical shape *across* layers: most slices deliver a thin end
of Layer 1, Layer 2 and Layer 3 together. A few slices remain Layer-1
foundation because the mechanic they add is orthogonal to which target is
being mutated and upgrades every layer at once.

### Vertical slices (most work)

Each vertical slice ships something real at all three layers:

- **L1**: a proposer variant, target class, or evaluation improvement.
- **L2**: a tool-surface capability an outer agentic loop can call.
- **L3**: a real, non-workflow target file the slice can point at end-to-end,
  with the safety story exercised on real code rather than asserted.

The first vertical slice (candidate `CHG-004`, not yet proposed) sketches
roughly as:

- **L1**: LangChain4j → real model proposes a bounded whole-file change; one
  candidate per run.
- **L2**: `evolve` exposed as an MCP tool over the existing CLI, so an outer
  agent can drive one run and read the result.
- **L3**: target is a single Java file inside this repository, mutated
  whole-file (no AST yet), gated by an existing unit test. Candidate isolation
  in a worktree is what keeps the blast radius bounded; the slice proves that
  on real code rather than a workflow fixture.

### Foundation slices (upgrade all layers at once)

- **Population**: several candidates per generation with deterministic ranking
  on identical evidence. This is the point at which SAAA starts to differ from
  "an agent with tests". `RISK-003` (candidate worktree name collisions) must
  be solved here.
- **Conceptual crossover**: `Q-005` and the deferred crossover policy in
  `AGENTS.md`. Recombine ideas from evaluated parents, not raw LLM-authored
  diffs.

These land as their own slices because they change the semantics of every
target — a population mechanic that only worked at Layer 1 would still need
re-deriving at Layer 3.

### Sizing rule

Each vertical slice is sized around the *hard* thing at each layer, not the
easy one. At Layer 2, exposing the CLI as MCP is a bolt-on; designing the tool
vocabulary an outer loop actually needs is design work. At Layer 3, whole-file
text mutation is trivial; AST-aware transformation is not. A slice that
budgets only for the easy thing at each layer silently slips.

### Blast radius at Layer 3

Mutating a workflow file that no one runs is a low-consequence experiment.
Mutating a Java file that participates in the build changes the safety story.
Candidate isolation in a Git worktree is already the answer here — nothing
lands in the working copy or in `main` without a human — but the first
vertical slice that touches real code must prove that story concretely, not
just assert it. Explicit constraints for the first Layer-3-included slice:

- one target file per candidate;
- the candidate branch is discardable;
- no auto-merge under any score;
- promotion produces a candidate branch pointer, not a merge to `main`;
- the `journal.md` writes into the target folder as today, not into the
  candidate branch history.

### What this replaces

An earlier draft of this ADR proposed a strict sequential order (live proposer
→ population → Layer 2 exposure → crossover → Layer 3). That order kept each
layer bounded but pushed the interesting Layer-2 and Layer-3 conversations far
into the future, and would have designed the outer-loop tool surface in
isolation from any real caller. The vertical-slice pattern trades bounded
per-step complexity for earlier real-use signal, which is more likely to
surface a wrong assumption while it is still cheap to fix.

The current right-sizing decision recorded in `PROJECT_PROFILE.toon` still
holds for foundation slices and for vertical slices up to but not including
AST-aware realization. AST-aware realization triggers a right-sizing revisit.

## Consequences

**Benefits.**

- One place to point at when the scope conversation reopens.
- The deterministic decision boundary (`ARCH-001`) gets stated at each layer,
  so it does not have to be re-derived per slice.
- The README can stop mixing "shipped today" with "where this is going".

**Costs and risks.**

- The three layers make SAAA sound bigger than it is today. The README summary
  section must state current-vs-planned bluntly so a reader is not misled.
- Layer 2 pulls Layer 1 into a role (a tool for someone else's agent) that
  affects API stability decisions earlier than a self-contained CLI would need
  to.
- Layer 3 is easy to over-promise. The "honest / not honest" split above is
  the guardrail; it should be repeated when Layer 3 work starts, not quietly
  softened.

**Revisit triggers.**

- The first Layer-3-touching vertical slice cannot demonstrate a safe,
  discardable candidate branch story on real code → pull Layer 3 back out of
  vertical slices until AST-aware realization or a stronger isolation story
  lands.
- Population slice ships but ranking is not measurably useful → the direction
  is wrong; stop taking vertical slices further into Layer 3 until the
  differentiator is real.
- Layer 2 exposure lands and an outer loop starts overriding scores in
  practice → reassert the boundary, add a hard-coded refusal, or acknowledge
  the boundary erosion in a follow-up ADR.
- Layer 3 investigation shows AST-aware mutation does not repay its complexity
  → keep hunks-only, or defer AST-aware realization indefinitely and cap
  Layer 3 targets accordingly.
- Anyone proposes making `PhenotypeFitnessScorer` (or another core scoring
  policy in `modules/deterministic`) a mutation target →
  **a superseding ADR is required** that names the guardrails proven to
  be in place. `CHG-004`'s staged-hybrid design defers this by choosing
  a small utility (`AuthorityLanguage`) as the first Layer-3 target and
  landing property tests plus a golden-verdict corpus as prerequisites;
  taking the next step needs an ADR that shows those prerequisites have
  real evidence of holding (typically: mutation-testing sweep against the
  scorer's test suite, an independent-judge scorer for the promotion
  decision, elitism against best-so-far on the golden corpus) rather than
  a spec change that quietly enables it. This trigger exists so a later
  agent cannot switch scorer-as-target on by editing configuration alone.

## Evidence

- `README.md` links here for the vision.
- `PROJECT_PROFILE.toon` records the current right-sizing decision.
- `specs/changes/CHG-003-first-vertical-slice/` is what Layer 1 ships today.
- `specs/changes/CHG-004-live-mcp-and-l3-utility/` is the first vertical
  slice per this ADR; its design document depends on the scorer-as-target
  revisit trigger above and its `change.toon` names scorer-as-target as a
  non-goal for the same reason.
- `.agents/knowledge/architecture/ARCH-001-deterministic-model-boundary.md`
  is the invariant this ADR extends across layers.
- `.agents/knowledge/questions/Q-005-crossover-policy.md` records the deferred
  recombination question that Step 4 in the build order answers.
