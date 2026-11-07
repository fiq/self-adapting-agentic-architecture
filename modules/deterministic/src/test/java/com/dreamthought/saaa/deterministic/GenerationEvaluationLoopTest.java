package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.deterministic.FitnessResultFixtures.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-026 T3. Evaluating a generation, one candidate at a time.
 *
 * <p>Sequential on purpose. Evaluating candidates in parallel would buy wall-clock time at the cost
 * of the property the ranking rests on, and ordering that depends on scheduling has produced two
 * defects in this repository already.
 */
final class GenerationEvaluationLoopTest {

    @Test
    @DisplayName("a candidate that produced no evidence is recorded with its reason and not ranked")
    void aCandidateThatProducedNoEvidenceIsRecordedAndNotRanked() {
        var evaluator = evaluatorReturning(
                () -> evaluation("candidate-a", 0.90, FitnessDecision.PROMOTE),
                () -> {
                    throw new IllegalStateException("candidate worktree already exists");
                },
                () -> evaluation("candidate-c", 0.85, FitnessDecision.PROMOTE));

        var generation = new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 3);

        assertThat(generation.ranked()).hasSize(2);
        assertThat(generation.unevaluated())
                .as("absent evidence is a recorded failure, never a candidate quietly dropped")
                .singleElement()
                .satisfies(entry -> assertThat(entry.reason()).contains("worktree already exists"));
        assertThat(generation.requestedCount())
                .as("a generation that ranked two of three must say three, or a systematic failure "
                        + "hides behind a plausible-looking winner")
                .isEqualTo(3);
        assertThat(generation.evaluatedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("every candidate in the generation is evaluated, and the result is ranked")
    void aGenerationEvaluatesEveryCandidateAndRanksWhatItGot() {
        var evaluator = evaluatorReturning(
                () -> evaluation("candidate-worse", 0.81, FitnessDecision.PROMOTE),
                () -> evaluation("candidate-best", 0.94, FitnessDecision.PROMOTE));

        var generation = new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 2);

        assertThat(generation.ranked())
                .extracting(candidate -> candidate.candidate().id())
                .containsExactly("candidate-best", "candidate-worse");
        assertThat(generation.winner()).isPresent();
    }

    @Test
    @DisplayName("a generation where every candidate failed claims nothing")
    void aGenerationThatEvaluatedNothingHasNoWinner() {
        var evaluator = evaluatorReturning(
                () -> {
                    throw new IllegalStateException("mutation validation failed");
                },
                () -> {
                    throw new IllegalStateException("mutation validation failed");
                });

        var generation = new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 2);

        assertThat(generation.ranked()).isEmpty();
        assertThat(generation.winner()).isEmpty();
        assertThat(generation.unevaluated()).hasSize(2);
        assertThat(generation.requestedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a generation of fewer than one candidate is refused")
    void aGenerationMustHaveAtLeastOneCandidate() {
        var evaluator = evaluatorReturning(() -> evaluation("candidate-a", 0.90, FitnessDecision.PROMOTE));

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one candidate");
    }

    @Test
    void aRepeatedMutationIsRecordedAsADuplicateRatherThanAborting() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-z", "MUT-same", "patch body", 0.81),
                () -> evaluationForMutation("candidate-a", "MUT-same", "patch body", 0.99),
                () -> evaluationForMutation("candidate-b", "MUT-other", "another proposal", 0.95));

        var generation = new GenerationEvaluationLoop(evaluator, candidate -> "one-tree").evaluate(request(), 3);

        assertThat(generation.ranked()).extracting(entry -> entry.candidate().id())
                .containsExactly("candidate-z");
        assertThat(generation.duplicates()).containsExactly(
                new com.dreamthought.saaa.domain.DuplicateCandidate("candidate-a", "candidate-z"),
                new com.dreamthought.saaa.domain.DuplicateCandidate("candidate-b", "candidate-z"));
        assertThat(generation.winner()).get().extracting(entry -> entry.candidate().id())
                .isEqualTo("candidate-z");
        assertThat(generation.spread()).isEmpty();
        assertThat(generation.unevaluated()).isEmpty();
        assertThat(generation.size()).isEqualTo(3);
        assertThat(generation.requestedCount()).isEqualTo(3);
        assertThat(generation.evaluatedCount()).isEqualTo(3);
    }

    @Test
    void candidatesWithDifferentTreesBothRankWhateverTheirIds() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-shared", "same proposal", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-shared", "same proposal", 0.85));

        var generation = new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 2);

        assertThat(generation.ranked()).extracting(entry -> entry.candidate().id())
                .containsExactly("candidate-a", "candidate-b");
        assertThat(generation.duplicates()).isEmpty();
        assertThat(generation.spread()).isPresent();
    }

    @Test
    void aTreeLookupFailureAbortsTheRunRatherThanRecordingAnUnevaluatedCandidate() {
        var failure = new IllegalStateException("tree lookup failed");
        var reasons = new ArrayList<String>();
        var counts = new ArrayList<Integer>();
        var reporter = new EvolutionReporter() {
            @Override
            public void generationAborted(int requested, int evaluated, String reason) {
                counts.add(requested);
                counts.add(evaluated);
                reasons.add(reason);
            }
        };
        var evaluator = evaluatorReturning(
                () -> evaluation("candidate-a", 0.9, FitnessDecision.PROMOTE),
                () -> evaluation("candidate-b", 0.8, FitnessDecision.PROMOTE),
                () -> evaluation("candidate-c", 0.7, FitnessDecision.PROMOTE));
        RealisedTreeIdentity identity = candidate -> {
            if (candidate.id().equals("candidate-c")) throw failure;
            return "same-tree";
        };

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator, identity)
                .evaluate(request(), 3, reporter)).isSameAs(failure);
        assertThat(counts).containsExactly(3, 2);
        assertThat(reasons).containsExactly("IllegalStateException: tree lookup failed");
    }

    @Test
    @DisplayName("a reporter that fails while recording an abort never replaces the abort's own cause")
    void aFailingAbortReporterDoesNotReplaceTheAbortCause() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-same", "patch body", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-same", "patch body", 0.90));
        var reporter = new EvolutionReporter() {
            @Override
            public void generationAborted(int requestedCount, int evaluatedCount, String reason) {
                throw new IllegalStateException("journal unwritable");
            }
        };

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator, candidate -> {
                    throw new IllegalStateException("tree lookup failed");
                }).evaluate(request(), 2, reporter))
                .hasMessageContaining("tree lookup failed")
                .satisfies(thrown -> assertThat(thrown.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("journal unwritable"));
    }

    @Test
    @DisplayName("candidates carrying different mutations are ranked as the population they are")
    void distinctMutationsAreRankedNormally() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-one", "patch one", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-two", "patch two", 0.85));

        var generation = new GenerationEvaluationLoop(evaluator, Candidate::id).evaluate(request(), 2);

        assertThat(generation.evaluatedCount()).isEqualTo(2);
        assertThat(generation.spread()).isPresent();
    }

    /** CHG-031. A failure after the proposer answered keeps its usage, and its reason stays readable. */
    @Test
    void anAttemptFailingAfterTheProposerAnsweredCarriesItsUsageIntoTheUnevaluatedEntry() {
        var spent = new com.dreamthought.saaa.domain.AgentUsage(
                java.util.OptionalLong.of(5), java.util.OptionalLong.of(2), Optional.empty(),
                java.util.OptionalLong.empty(), java.util.OptionalInt.empty(), Optional.empty());
        var loop = new GenerationEvaluationLoop(evaluatorReturning(
                () -> { throw new AttemptFailedAfterProposalException(
                        new IllegalStateException("mutation validation failed: scope"), spent); },
                () -> { throw new IllegalStateException("transport"); }), Candidate::id);

        var generation = loop.evaluate(request(), 2);

        assertThat(generation.unevaluated()).hasSize(2);
        assertThat(generation.unevaluated().get(0).reason())
                .isEqualTo("IllegalStateException: mutation validation failed: scope");
        assertThat(generation.unevaluated().get(0).usage()).contains(spent);
        assertThat(generation.unevaluated().get(1).usage()).isEmpty();
    }

    /** A result whose mutation identity the test does not care about, distinct per candidate id. */
    private static CandidateEvaluation evaluation(
            String candidateId, double magnitude, FitnessDecision decision) {
        return evaluationForMutation(
                candidateId, "MUT-" + candidateId, "patch for " + candidateId, magnitude);
    }

    private static CandidateEvaluation evaluationForMutation(
            String candidateId, String mutationId, String patch, double magnitude) {
        var scored = result(candidateId, magnitude, FitnessDecision.PROMOTE);
        var mutation = new Mutation(mutationId, "summary of " + mutationId,
                MutationScope.WORKFLOW_DEFINITION, patch);
        var resultForMutation = new FitnessResult(
                new Candidate(candidateId, mutationId, scored.candidate().branchName(),
                        scored.candidate().worktreePath(), scored.candidate().commitSha()),
                scored.evidence(), scored.objectives(), scored.fitnessScore(), scored.scoringContext());
        return new CandidateEvaluation(mutation, resultForMutation);
    }

    @SafeVarargs
    private static CandidateEvaluator evaluatorReturning(
            java.util.function.Supplier<CandidateEvaluation>... outcomes) {
        Deque<java.util.function.Supplier<CandidateEvaluation>> remaining =
                new ArrayDeque<>(List.of(outcomes));
        var seen = new ArrayList<MutationProposalRequest>();
        return request -> {
            seen.add(request);
            if (remaining.isEmpty()) {
                throw new AssertionError("the loop asked for more candidates than the test supplied");
            }
            return remaining.removeFirst().get();
        };
    }

    private static MutationProposalRequest request() {
        var baseline = new WorkflowGraph("toy-workflow", "0".repeat(40), "steps: []\n");
        return new MutationProposalRequest(
                baseline,
                new RetrievalQuery(RetrievalMode.NONE, "task", baseline, "0".repeat(40),
                        List.of("workflow.yaml"), Optional.empty()));
    }
}
