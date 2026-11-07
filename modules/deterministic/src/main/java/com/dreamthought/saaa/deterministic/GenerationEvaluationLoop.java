package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.DuplicateCandidate;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates a generation of candidates against one baseline and ranks what it got.
 *
 * <p>Sequential, one candidate at a time. Parallel evaluation would buy wall-clock time at the cost
 * of the property the ranking rests on, and the recorded right-sizing is one candidate evaluation at
 * a time. That is a decision to revisit against evidence that the wall clock is the problem, not a
 * limitation to route around.
 *
 * <p>Identity is the realised committed tree, regardless of mutation id or patch. The first
 * candidate with a tree ranks; later matches are recorded as duplicates. Identity lookup failures
 * abort the run because they prevent a trustworthy comparison.
 */
public final class GenerationEvaluationLoop {
    private final CandidateEvaluator evaluator;
    private final PopulationRankingPolicy ranking;
    private final RealisedTreeIdentity treeIdentity;

    public GenerationEvaluationLoop(CandidateEvaluator evaluator, RealisedTreeIdentity treeIdentity) {
        this(evaluator, treeIdentity, new PopulationRankingPolicy());
    }

    public GenerationEvaluationLoop(
            CandidateEvaluator evaluator, RealisedTreeIdentity treeIdentity, PopulationRankingPolicy ranking) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.treeIdentity = Objects.requireNonNull(treeIdentity, "treeIdentity");
        this.ranking = Objects.requireNonNull(ranking, "ranking");
    }

    /**
     * Evaluates {@code candidates} candidates and ranks the first for each distinct realised tree.
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
        var duplicates = new ArrayList<DuplicateCandidate>();
        var firstByTree = new LinkedHashMap<String, String>();
        String fingerprint = null;
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
                    RuntimeException cause = failure;
                    Optional<AgentUsage> spent = Optional.empty();
                    if (failure instanceof AttemptFailedAfterProposalException answered) {
                        cause = answered.getCause();
                        spent = Optional.of(answered.usage());
                    }
                    unevaluated.add(new UnevaluatedCandidate(
                            "attempt-" + attempt + "-of-" + candidates, describe(cause), spent));
                    continue;
                }
                var result = evaluation.fitnessResult();
                if (fingerprint != null && !fingerprint.equals(result.scoringFingerprint())) {
                    throw new IllegalArgumentException("a generation cannot mix scoring fingerprints: "
                            + fingerprint + ", " + result.scoringFingerprint());
                }
                fingerprint = result.scoringFingerprint();
                String tree = Objects.requireNonNull(treeIdentity.treeOf(result.candidate()), "realised tree");
                String first = firstByTree.putIfAbsent(tree, result.candidate().id());
                if (first == null) {
                    evaluated.add(result);
                } else {
                    duplicates.add(new DuplicateCandidate(result.candidate().id(), first));
                }
            }
            var ranked = ranking.rank(evaluated, unevaluated);
            return new RankedGeneration(ranked.ranked(), ranked.unevaluated(), duplicates);
        } catch (RuntimeException failure) {
            // Reporting the abort is presentation. If it fails too, the abort is still the thing
            // the caller must see, so the reporter's failure rides along as suppressed rather than
            // replacing it.
            try {
                reporter.generationAborted(candidates, evaluated.size() + duplicates.size(), describe(failure));
            } catch (RuntimeException reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            throw failure;
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
