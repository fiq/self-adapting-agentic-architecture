package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates a generation of candidates against one baseline and ranks what it got.
 *
 * <p>Sequential, one candidate at a time. Parallel evaluation would buy wall-clock time at the cost
 * of the property the ranking rests on, and the recorded right-sizing is one candidate evaluation at
 * a time. That is a decision to revisit against evidence that the wall clock is the problem, not a
 * limitation to route around.
 *
 * <p>The single-candidate path is untouched: this composes {@link CandidateEvaluator}, which
 * {@code MutationEvaluationLoop::evaluate} already satisfies without changing that class, so a
 * caller evaluating one candidate today behaves exactly as it did.
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
        Objects.requireNonNull(request, "request");
        if (candidates < 1) {
            throw new IllegalArgumentException(
                    "a generation needs at least one candidate, asked for " + candidates);
        }

        var evaluated = new ArrayList<FitnessResult>();
        var unevaluated = new ArrayList<UnevaluatedCandidate>();
        var mutations = new LinkedHashSet<String>();
        for (int attempt = 1; attempt <= candidates; attempt++) {
            FitnessResult result;
            try {
                result = Objects.requireNonNull(
                        evaluator.evaluate(request), "a candidate evaluation returned no result");
            } catch (RuntimeException failure) {
                // Named by attempt rather than by candidate id, because a realisation that failed
                // before a candidate existed has no id to record and inventing one would imply a
                // candidate that was never created.
                unevaluated.add(new UnevaluatedCandidate(
                        "attempt-" + attempt + "-of-" + candidates, describe(failure)));
                continue;
            }
            requireMutationNotAlreadySeen(mutations, result, attempt, candidates);
            evaluated.add(result);
        }
        return ranking.rank(evaluated, unevaluated);
    }

    /**
     * Fails the run when two candidates carry the same mutation.
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
     */
    private static void requireMutationNotAlreadySeen(
            Set<String> seen, FitnessResult result, int attempt, int candidates) {
        String mutationId = result.candidate().mutationId();
        if (!seen.add(mutationId)) {
            throw new IllegalStateException(
                    "candidate " + attempt + " of " + candidates + " repeats mutation " + mutationId
                            + ", so this generation is one candidate evaluated more than once rather "
                            + "than a population; the proposer produced no variant for it");
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
