package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a generation of candidates against one baseline and ranks what it got.
 *
 * <p>Sequential, one candidate at a time. Parallel evaluation would buy wall-clock time at the cost
 * of the property the ranking rests on, and the recorded right-sizing is one candidate evaluation at
 * a time. That is a decision to revisit against evidence that the wall clock is the problem, not a
 * limitation to route around.
 *
 * <p>The evaluator returns the mutation alongside the scored result so the identity rule below can
 * compare what was proposed rather than only what was recorded. The single-candidate path is
 * untouched: {@code MutationEvaluationLoop::evaluate} keeps its signature for its current callers
 * and only the widened {@code evaluateCandidate} carries the mutation.
 */
public final class GenerationEvaluationLoop {
    private final CandidateEvaluator evaluator;
    private final PopulationRankingPolicy ranking;

    public GenerationEvaluationLoop(CandidateEvaluator evaluator) {
        this(evaluator, new PopulationRankingPolicy());
    }

    public GenerationEvaluationLoop(CandidateEvaluator evaluator, PopulationRankingPolicy ranking) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.ranking = Objects.requireNonNull(ranking, "ranking");
    }

    /**
     * Evaluates {@code candidates} candidates and ranks the ones that produced evidence.
     *
     * <p>A candidate that fails outright does not abort the generation. It is recorded with its
     * reason and the rest continue, because one unrealisable mutation says nothing about the others
     * and losing the whole generation to it would waste every evaluation already done.
     *
     * <p>The catch is deliberately broad. Anything a candidate's evaluation throws — a validation
     * failure, a worktree collision, a check runner that could not start — means the same thing to a
     * generation: this candidate produced no evidence. Narrowing it to the exception types known
     * today would let a new failure mode abort the generation instead of being recorded, which is the
     * outcome this exists to prevent. {@code Error} still propagates.
     */
    public RankedGeneration evaluate(MutationProposalRequest request, int candidates) {
        return evaluate(request, candidates, EvolutionReporter.NO_OP);
    }

    /**
     * Evaluates a generation while reporting an abort before its cause propagates to the caller.
     */
    public RankedGeneration evaluate(
            MutationProposalRequest request, int candidates, EvolutionReporter reporter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reporter, "reporter");
        if (candidates < 1) {
            throw new IllegalArgumentException(
                    "a generation needs at least one candidate, asked for " + candidates);
        }

        var evaluated = new ArrayList<FitnessResult>();
        var unevaluated = new ArrayList<UnevaluatedCandidate>();
        // Mutation id -> every patch seen under it. Sameness is the pair (id, patch): the same id
        // over a different patch is two mutations and ranks, which is the Q-013 settlement.
        var mutations = new LinkedHashMap<String, List<String>>();
        try {
            for (int attempt = 1; attempt <= candidates; attempt++) {
                CandidateEvaluation evaluation;
                try {
                    evaluation = Objects.requireNonNull(
                            evaluator.evaluate(request), "a candidate evaluation returned no result");
                } catch (RuntimeException failure) {
                    // Named by attempt rather than by candidate id, because a realisation that failed
                    // before a candidate existed has no id to record and inventing one would imply a
                    // candidate that was never created.
                    unevaluated.add(new UnevaluatedCandidate(
                            "attempt-" + attempt + "-of-" + candidates, describe(failure)));
                    continue;
                }
                requireMutationNotAlreadySeen(mutations, evaluation, attempt, candidates);
                evaluated.add(evaluation.fitnessResult());
            }
            return ranking.rank(evaluated, unevaluated);
        } catch (RuntimeException failure) {
            // Reporting the abort is presentation. If it fails too, the abort is still the thing
            // the caller must see, so the reporter's failure rides along as suppressed rather than
            // replacing it.
            try {
                reporter.generationAborted(candidates, evaluated.size(), describe(failure));
            } catch (RuntimeException reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            throw failure;
        }
    }

    /**
     * Fails the run when two candidates carry the same mutation: the same id over the same patch.
     *
     * <p>Candidate ids are kept apart by the run's namespace, so one candidate evaluated N times
     * still produces N distinct ids, N worktrees and N rows. It reads exactly like a population and
     * is not one: the ranking would be a tie broken by candidate id, and the spread would be zero
     * for a reason that has nothing to do with the candidates.
     *
     * <p>Thrown rather than recorded as an unevaluated candidate, because this is not a candidate
     * that failed. It is the proposer failing to vary, which makes the whole generation meaningless,
     * and a generation that quietly returned a tie would answer ADR-0002's "is ranking measurably
     * useful" question with an artefact of its own wiring.
     *
     * <p>The comparison is on the pair (mutation id, patch), which settles Q-013 in code. A model
     * chooses its own ids as free text, so one id over two genuinely different patches is two
     * mutations and ranks; the check fires only when the patch also matches, which is exactly the
     * case where the proposer produced no variant, and the message says so. Patches are compared as
     * exact content. Identity derived from the realised tree rather than the proposal remains the
     * recorded destination - the same instinct as the declared-locus gate - and is not built here.
     */
    private static void requireMutationNotAlreadySeen(
            Map<String, List<String>> seen, CandidateEvaluation evaluation, int attempt, int candidates) {
        var mutation = evaluation.mutation();
        var patches = seen.computeIfAbsent(mutation.id(), id -> new ArrayList<>());
        if (patches.contains(mutation.patch())) {
            throw new IllegalStateException(
                    "candidate " + attempt + " of " + candidates + " repeats mutation id "
                            + mutation.id() + " with the same patch, so this generation cannot be "
                            + "ranked: the proposer produced no variant for it. Two candidates are "
                            + "the same mutation only when the id and the patch both match; the "
                            + "same id over a different patch would have ranked as its own candidate");
        }
        patches.add(mutation.patch());
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
