# CHG-026: A population of candidates, ranked on identical evidence

## Why

SAAA evaluates one candidate per run. It realises the change, runs the checks,
scores it and decides `PROMOTE` or `DISCARD`. That is a rigorous version of what
an agent with a test suite already does: propose, run, keep if green.

Selection is different in kind. When several candidates are evaluated against the
same baseline under the same measurement configuration, "which of these is
better" stops being an opinion and becomes a fact fixed code can establish. That
is the mechanism the whole genetic-programming lineage rests on, and `ADR-0002`
names it as the point at which SAAA starts to differ from an agent with tests. It
is the piece that ADR records as missing from Layer 1.

The README makes the same argument from the other end: without a population, a
good idea inside a rejected attempt rarely survives into the next one, because
each attempt produces an answer with no shared axis to compare it against.

## What was blocking it, and is no longer

Ranking N candidates needs a scorer that can tell them apart. Until recently this
one could not, and shipping ranking onto it would have walked straight into
`ADR-0002`'s own revisit trigger — *population slice ships but ranking is not
measurably useful*. Four changes fixed that and are now on `main`:

- `CHG-021` made the scorer discriminate among failures;
- `CHG-022` made it discriminate among winners;
- `CHG-023` made `FitnessScore` order decision-first, so a promotion always
  outranks a discard and two discards remain separable by magnitude;
- `CHG-024` added held-out behaviour cases, so `task_success` can vary among
  promoted candidates, and a `ScoringContext` fingerprint so scores produced
  under different configurations are never silently compared.

That last one matters more here than where it was introduced. A single-candidate
run has nothing to compare against, so the fingerprint guards a future. A
population is that future: it is the first place two scores meet.

## Intent

Evaluate several candidates against one baseline in a single generation, rank
them deterministically on identical evidence, and record the ranking as durable
evidence.

Ranking selects among candidates the gates already promoted. It is not a second
opinion on the gates, and it may never promote something they discarded.

## What this needs that does not exist

**A per-candidate worktree name.** `GitCandidateWorkspace` already accepts a
`candidateNamespace` and folds it into the candidate id, the branch and the
worktree path. Only `BenchmarkCommand` ever supplies one; the ordinary evolve
path leaves it empty, which is why `RISK-003` is still live — two candidates
deriving a name from workflow id and mutation id alone collide, and the second
fails with `candidate worktree already exists`. The mechanism is there and needs
a per-candidate value, not a redesign.

**Something to vary.** `FixtureMutationProposer` returns
`MUT-<baseline>-fixture` on every call. Asking it for three candidates yields the
same candidate three times, which then collide on that path. So a population needs
either the live model proposer the README also lists as missing, or a fixture
proposer that varies deliberately. This change takes the second route: a canned
population proves the mechanism, exactly as a canned proposer today proves the
pipe rather than that a model has good ideas. Wiring the live proposer stays a
separate change so that a ranking defect and a proposer defect cannot arrive
together and be mistaken for each other.

## Scope: one generation, evaluated sequentially

One round of N candidates, ranked, recorded. No iteration into a second
generation, which is where elitism and an archive become necessary and where the
question of what survives needs its own decision.

Sequential evaluation, not parallel. The recorded right-sizing is one candidate
evaluation at a time, ordering nondeterminism has twice produced real defects in
this repository, and N candidates in parallel would buy wall-clock time at the
cost of the property the ranking depends on. Parallelism is an optimisation to
take later, against evidence that the wall clock is the problem.

## Non-goals

- conceptual crossover, which `ADR-0002` sequences after population and which
  `ConceptualCrossoverPolicy` already has unit tests for and no wiring;
- iterated generations, elitism, or an archive of best-so-far;
- the live model proposer;
- parallel candidate evaluation;
- automatic worktree cleanup, which interacts with the rule that a dirty
  worktree is never removed;
- AST-aware realisation or the declared-locus gate, which is the other track.

## The honest risk

If ranking turns out not to discriminate — if every candidate in a generation
lands at the same score — that is not a defect in this change, it is `ADR-0002`'s
revisit trigger firing, and it means the direction is wrong before more is built
on top. So the generation record must carry the spread of scores, not only the
winner. A slice that cannot tell you whether it helped is the thing this project
exists to argue against.

## Related knowledge

`ADR-0002`, `ARCH-001`, `CON-002`, `SYS-001`, `RISK-003`, `PAT-004`.
