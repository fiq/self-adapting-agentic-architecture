package io.github.selfadaptingagenticarchitecture.application;

import static io.github.selfadaptingagenticarchitecture.core.FitnessDecision.DISCARD;
import static io.github.selfadaptingagenticarchitecture.core.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.selfadaptingagenticarchitecture.core.BenchmarkEvidence;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.CheckEvidence;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.ValidationResult;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MutationEvaluationLoopAcceptanceTest {
    @Test
    void evaluatesOneCandidateAndDiscardsWhenFitnessFailsThreshold() {
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var mutation = new Mutation("mut-001", "tighten tool selection", WORKFLOW_DEFINITION, "replace tool policy");
        var candidate = new Candidate(
                "cand-001",
                mutation.id(),
                "candidate/mut-001",
                Path.of(".worktrees/candidate-mut-001"),
                "abc1234"
        );
        var decisions = new RecordingDecisionSink();
        var metadata = new RecordingMetadataStore();

        var loop = new MutationEvaluationLoop(
                ignored -> mutation,
                (workflow, proposed) -> ValidationResult.passed(),
                (workflow, proposed) -> candidate,
                ignored -> List.of(CheckEvidence.passed("gradle-test", "all deterministic checks passed")),
                ignored -> List.of(BenchmarkEvidence.measurement("sample-throughput", 42.0, "ops/s")),
                (evaluatedCandidate, evidence) -> new FitnessResult(
                        evaluatedCandidate,
                        evidence,
                        Map.of("correctness", 1.0, "throughput", 0.4),
                        0.59,
                        DISCARD
                ),
                metadata,
                decisions
        );

        var result = loop.evaluate(baseline);

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.evidence().checksPassed()).isTrue();
        assertThat(metadata.recordedCandidates()).containsExactly(candidate);
        assertThat(metadata.recordedFitness()).contains(result);
        assertThat(decisions.discardedCandidate()).contains(candidate);
        assertThat(decisions.promotedCandidate()).isEmpty();
    }

    private static final class RecordingMetadataStore implements ExperimentMetadataStore {
        private final List<Candidate> candidates = new ArrayList<>();
        private FitnessResult recordedFitness;

        @Override
        public void recordCandidate(Candidate candidate) {
            candidates.add(candidate);
        }

        @Override
        public void recordFitness(FitnessResult result) {
            recordedFitness = result;
        }

        Optional<FitnessResult> recordedFitness() {
            return Optional.ofNullable(recordedFitness);
        }

        List<Candidate> recordedCandidates() {
            return List.copyOf(candidates);
        }
    }

    private static final class RecordingDecisionSink implements CandidateDecisionSink {
        private Candidate promotedCandidate;
        private Candidate discardedCandidate;

        @Override
        public void promote(Candidate candidate, FitnessResult result) {
            promotedCandidate = candidate;
        }

        @Override
        public void discard(Candidate candidate, FitnessResult result) {
            discardedCandidate = candidate;
        }

        Optional<Candidate> promotedCandidate() {
            return Optional.ofNullable(promotedCandidate);
        }

        Optional<Candidate> discardedCandidate() {
            return Optional.ofNullable(discardedCandidate);
        }
    }
}
