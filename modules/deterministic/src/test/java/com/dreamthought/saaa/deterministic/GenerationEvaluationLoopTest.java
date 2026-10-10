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

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 3);

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

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

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

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

        assertThat(generation.ranked()).isEmpty();
        assertThat(generation.winner()).isEmpty();
        assertThat(generation.unevaluated()).hasSize(2);
        assertThat(generation.requestedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a generation of fewer than one candidate is refused")
    void aGenerationMustHaveAtLeastOneCandidate() {
        var evaluator = evaluatorReturning(() -> evaluation("candidate-a", 0.90, FitnessDecision.PROMOTE));

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator).evaluate(request(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one candidate");
    }

    /**
     * CHG-029 S3, and the rule a fixture proposer that produced no variant must still fail under.
     * A proposer that returns the same mutation every time still produces distinct candidate ids,
     * because the run's namespace keeps them apart, so nothing downstream notices: N worktrees, N
     * rows, a ranking that is a tie broken by candidate id and a spread of zero that says nothing
     * about the candidates.
     *
     * <p>The run fails rather than recording the repeat as an unevaluated candidate. This is not a
     * candidate that failed, it is the proposer failing to vary, and a generation that returned the
     * tie quietly would answer ADR-0002's "is ranking measurably useful" question with an artefact
     * of its own wiring.
     *
     * <p>Sameness is decided on the pair (mutation id, patch), which settles Q-013: when the id and
     * the patch both repeat, the one remaining cause is that the proposer produced no variant, so
     * the message names that cause and says the patch matched. Under the old id-only rule the same
     * message had to hedge, because a live proposer can legitimately reuse an id over a different
     * patch.
     */
    @Test
    @DisplayName("the same mutation id over the same patch fails the generation naming non-variation")
    void theSameMutationIdOverTheSamePatchFailsNamingNonVariation() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-same", "patch body", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-same", "patch body", 0.90));

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator).evaluate(request(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MUT-same")
                .hasMessageContaining("cannot be ranked")
                // The diagnosis names the one case this can now be: the id and the patch both
                // matched, so the proposer produced no variant for the second candidate.
                .hasMessageContaining("same patch")
                .hasMessageContaining("produced no variant");
    }

    /**
     * CHG-029 S4. A model chooses its own ids as free text, so one id over two genuinely different
     * patches is two mutations, not one mutation ranked against itself. Failing that run was the
     * half-true diagnosis Q-013 recorded: the id matched and the substance did not.
     */
    @Test
    @DisplayName("the same mutation id over different patches is two candidates and ranks")
    void theSameMutationIdOverDifferentPatchesRanks() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-shared", "patch one", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-shared", "patch two", 0.85));

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

        assertThat(generation.evaluatedCount())
                .as("both candidates were evaluated rather than one refused as a repeat")
                .isEqualTo(2);
        assertThat(generation.ranked())
                .extracting(entry -> entry.candidate().id())
                .containsExactly("candidate-a", "candidate-b");
        assertThat(generation.spread()).isPresent();
    }

    /**
     * The other half of the pair rule: only a repeated id over a repeated patch is non-variation.
     * Two candidates whose ids differ are two mutations even when their patches are byte-identical
     * - whether identical bodies under distinct ids should count as one candidate is the
     * realised-tree identity Q-013 records as the destination, and this slice deliberately does not
     * build it.
     */
    @Test
    @DisplayName("different mutation ids over the same patch still rank as two candidates")
    void differentMutationIdsOverTheSamePatchRank() {
        var evaluator = evaluatorReturning(
                () -> evaluationForMutation("candidate-a", "MUT-one", "identical patch", 0.90),
                () -> evaluationForMutation("candidate-b", "MUT-two", "identical patch", 0.85));

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

        assertThat(generation.evaluatedCount()).isEqualTo(2);
        assertThat(generation.ranked()).hasSize(2);
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

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator).evaluate(request(), 2, reporter))
                .hasMessageContaining("MUT-same")
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

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

        assertThat(generation.evaluatedCount()).isEqualTo(2);
        assertThat(generation.spread()).isPresent();
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
