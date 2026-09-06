package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.deterministic.FitnessResultFixtures.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;
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
                () -> result("candidate-a", 0.90, FitnessDecision.PROMOTE),
                () -> {
                    throw new IllegalStateException("candidate worktree already exists");
                },
                () -> result("candidate-c", 0.85, FitnessDecision.PROMOTE));

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
                () -> result("candidate-worse", 0.81, FitnessDecision.PROMOTE),
                () -> result("candidate-best", 0.94, FitnessDecision.PROMOTE));

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
        var evaluator = evaluatorReturning(() -> result("candidate-a", 0.90, FitnessDecision.PROMOTE));

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator).evaluate(request(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one candidate");
    }

    /**
     * S8, at the level that can enforce it. A proposer that returns the same mutation every time
     * still produces distinct candidate ids, because the run's namespace keeps them apart, so
     * nothing downstream notices: N worktrees, N rows, a ranking that is a tie broken by candidate
     * id and a spread of zero that says nothing about the candidates.
     *
     * <p>The run fails rather than recording the repeat as an unevaluated candidate. This is not a
     * candidate that failed, it is the proposer failing to vary, and a generation that returned the
     * tie quietly would answer ADR-0002's "is ranking measurably useful" question with an artefact
     * of its own wiring.
     */
    @Test
    @DisplayName("a generation that evaluated one mutation more than once fails rather than ranking it against itself")
    void aRepeatedMutationFailsTheGenerationRatherThanRankingOneCandidateTwice() {
        var evaluator = evaluatorReturning(
                () -> resultForMutation("candidate-a", "MUT-same", 0.90),
                () -> resultForMutation("candidate-b", "MUT-same", 0.90));

        assertThatThrownBy(() -> new GenerationEvaluationLoop(evaluator).evaluate(request(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MUT-same")
                .hasMessageContaining("cannot be ranked")
                // The diagnosis names both causes rather than only the fixture one. A live proposer
                // chooses its own ids, so a repeat there may be a different mutation under a reused
                // id, and a message asserting "produced no variant" would be half false.
                .hasMessageContaining("produced no variant")
                .hasMessageContaining("different mutation under an id it had already used");
    }

    @Test
    @DisplayName("candidates carrying different mutations are ranked as the population they are")
    void distinctMutationsAreRankedNormally() {
        var evaluator = evaluatorReturning(
                () -> resultForMutation("candidate-a", "MUT-one", 0.90),
                () -> resultForMutation("candidate-b", "MUT-two", 0.85));

        var generation = new GenerationEvaluationLoop(evaluator).evaluate(request(), 2);

        assertThat(generation.evaluatedCount()).isEqualTo(2);
        assertThat(generation.spread()).isPresent();
    }

    private static FitnessResult resultForMutation(String candidateId, String mutationId, double magnitude) {
        var scored = result(candidateId, magnitude, FitnessDecision.PROMOTE);
        return new FitnessResult(
                new Candidate(candidateId, mutationId, scored.candidate().branchName(),
                        scored.candidate().worktreePath(), scored.candidate().commitSha()),
                scored.evidence(), scored.objectives(), scored.fitnessScore(), scored.scoringContext());
    }

    @SafeVarargs
    private static CandidateEvaluator evaluatorReturning(java.util.function.Supplier<FitnessResult>... outcomes) {
        Deque<java.util.function.Supplier<FitnessResult>> remaining = new ArrayDeque<>(List.of(outcomes));
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
