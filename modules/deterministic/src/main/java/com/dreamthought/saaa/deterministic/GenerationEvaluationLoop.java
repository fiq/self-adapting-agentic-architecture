package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.util.ArrayList;
import java.util.Objects;

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
        for (int attempt = 1; attempt <= candidates; attempt++) {
            try {
                evaluated.add(Objects.requireNonNull(
                        evaluator.evaluate(request), "a candidate evaluation returned no result"));
            } catch (RuntimeException failure) {
                // Named by attempt rather than by candidate id, because a realisation that failed
                // before a candidate existed has no id to record and inventing one would imply a
                // candidate that was never created.
                unevaluated.add(new UnevaluatedCandidate(
                        "attempt-" + attempt + "-of-" + candidates, describe(failure)));
            }
        }
        return ranking.rank(evaluated, unevaluated);
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
