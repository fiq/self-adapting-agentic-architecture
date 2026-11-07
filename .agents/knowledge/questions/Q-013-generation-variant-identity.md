---
id: Q-013
type: question
title: What identifies two candidates in a generation as the same mutation
status: answered
resolution: Answered by CHG-032 (specs/changes/CHG-032-realised-tree-identity). Two candidates are the same when their commits hold the same files, the audit envelope under .saaa/candidates/ excepted. The first ranks; later ones are recorded as duplicates. CHG-029's (id, patch) rule is retired.
summary: Answered by CHG-032. Identity is the realised tree - the committed files minus the candidate's own audit envelope - so three ids over one file rank once and the other two are recorded as duplicates. CHG-029's (id, patch) rule, which aborted the run, is retired. Originally - a generation refuses to rank one mutation against itself, and it decides sameness by mutation id alone. That is exact for the fixture proposer, whose ids it controls, and blunt for a live proposer, whose ids are model-chosen free text. A model returning one id over two genuinely different patches would fail the run with a diagnosis that is only half true. Comparing bodies needs the mutation to reach the deterministic rule, and a scored result carries its candidate rather than the mutation that produced it.
owners:
  - architect
  - tech-lead
relates_to:
  - PAT-004
decisions:
  - ADR-0002
evidence:
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/GenerationEvaluationLoop.java
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/langchain4j/LangChain4jMutationProposalAdapter.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationProposer.java
  - specs/changes/CHG-029-live-generation-usage/change.toon
  - specs/changes/CHG-032-realised-tree-identity/change.toon
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitRealisedTreeIdentity.java
review_after: 2026-12-31
---

# Generation Variant Identity

## Answer (CHG-032)

The third candidate answer below is now built.

- **Identity is what landed.** `GitRealisedTreeIdentity` hashes every committed
  path, mode and blob id of a candidate's commit. It skips `.saaa/candidates/`,
  because each candidate commits its own audit envelope there and the envelope
  names the candidate. The envelope stays in the commit; it is the durable
  record of why the candidate exists.
- **First seen ranks; the rest are duplicates.** A later candidate with the
  same identity is recorded as a duplicate naming the first, kept out of the
  ranking, the winner and the spread, written to `generation_duplicates`, and
  shown in the journal and console. Its usage stays in `candidate_usage`, so a
  generation's whole spend is unchanged.
- **No abort.** A repeated `(id, patch)` realises the same files, so it is now
  a duplicate rather than a failed run. A non-varying proposer shows as one
  ranked candidate and N-1 duplicates.
- **A workflow file inside `.saaa/candidates/` is refused**, since identity
  would not see it.
- **Not built:** semantic sameness (different files, same behaviour) and
  identity across generations or runs.


## Earlier answer (CHG-029, superseded)

The first and second candidate answers below were both taken; CHG-032 has since
built the third.

- **The check is `(id, patch)`.** `GenerationEvaluationLoop` now receives the
  mutation with each result (`CandidateEvaluation`) and fails the generation only
  when an id and its exact patch content both repeat, with a message that says
  the proposer produced no variant. The same id over a different patch ranks.
- **The variant is also asked for.** The live proposer puts the variant number
  in the model prompt. That is how it is asked to vary, never how variation is
  checked.
- **Not built: identity from the realised tree.** One known consequence of
  comparing the proposal rather than the realisation: two different ids over an
  identical patch still rank as two candidates. That is the residue this
  question leaves, and the realised-tree rule is what would close it.

## Observed live (2026-11-07)

The residue is no longer hypothetical. Run `nw4` (neuralwatt
`glm-5.3-flash-flex`, three candidates, toy fixture, a stated goal) produced
ids `toy-safe-publish-gated-enforce`, `toy-safe-publish-gated` and
`toy-mut3-draft-enforce-publish-gated`, each realising exactly
`publish-policy: gated` / `draft-check: enforce`. All three promoted at 0.99
and ranked as a population of three with spread 0.00. The variant ask did not
make the model vary. Identity from the realised tree is now the next step for
this question rather than a recorded destination.

## What prompted it

Independent review of `CHG-026`, the population slice. A generation fails the
run when two candidates carry the same mutation id, because otherwise the run's
namespace gives them distinct candidate ids, distinct worktrees and a ranking
that looks like a population and is not one.

The reviewer pointed out that the rule is right and the instrument is wrong for
one of the two proposers it governs.

- `FixtureMutationProposer` chooses its own ids, so a repeated id there means
  deterministic non-variation with certainty.
- `LangChain4jMutationProposalAdapter` takes `id` straight from the model's JSON
  with only non-blank and length bounds, and neither it nor
  `AgentHarnessMutationProposer` overrides the variant entry point. The variant
  number never reaches the model prompt either.

So a live proposer asked for a generation can return one id over two different
patches, and the run dies at candidate two after paying for two full
evaluations, leaving two fitness rows and no generation row.

## Why it is not fixed in CHG-026

A live proposer filling a generation is an explicit non-goal of that change,
kept separate so a ranking defect and a proposer defect could not arrive
together and be mistaken for each other. The failure is loud and leaves no
corrupted record, so it is a usability defect on a path the change scopes out
rather than a correctness hole in what it delivers.

The message was corrected to name both causes rather than asserting the fixture
one.

## What would answer it

Three candidate answers, in rough order of preference:

- **Compare `(id, patch)`.** Same id and same body is non-variation and keeps
  the hard failure, where the diagnosis is true. Same id and different body is
  two mutations and should rank. This needs the mutation to reach the rule:
  `CandidateEvaluator` returns a `FitnessResult`, which carries the `Candidate`
  and not the `Mutation`, so either the result or the port has to widen.
- **Put the variant number in the model prompt** and require distinct ids, so
  the proposer is asked to vary rather than checked for having varied. Weaker,
  because it trusts the model to comply and the check exists precisely because
  that trust is what the slice must not require.
- **Derive identity from the realised tree** rather than from what the proposer
  claimed. Strongest, because it measures what actually landed rather than what
  was asserted, and it is the only one that survives a proposer that lies. Also
  the most work.

The first is the smallest change that makes the diagnosis true. The third is
where this probably ends up, and it is the same instinct as the declared-locus
gate: check the realisation, not the declaration.
