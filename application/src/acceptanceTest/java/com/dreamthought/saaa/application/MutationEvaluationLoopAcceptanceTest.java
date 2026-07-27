package com.dreamthought.saaa.application;

import static com.dreamthought.saaa.core.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.core.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.core.BenchmarkEvidence;
import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.CheckEvidence;
import com.dreamthought.saaa.core.FitnessResult;
import com.dreamthought.saaa.core.Mutation;
import com.dreamthought.saaa.core.ValidationResult;
import com.dreamthought.saaa.core.WorkflowGraph;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        var evaluatedAt = Instant.parse("2026-07-27T00:00:00Z");

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
                decisions,
                Clock.fixed(evaluatedAt, ZoneOffset.UTC)
        );

        var result = loop.evaluate(baseline);

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.evidence().checksPassed()).isTrue();
        assertThat(result.evidence().evaluatedAt()).isEqualTo(evaluatedAt);
        assertThat(metadata.recordedCandidates()).containsExactly(candidate);
        assertThat(metadata.recordedFitness()).contains(result);
        assertThat(decisions.discardedCandidate()).contains(candidate);
        assertThat(decisions.promotedCandidate()).isEmpty();
    }

    @Test
    void rejectsFitnessResultForCandidateThatWasNotEvaluated() {
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var mutation = new Mutation("mut-001", "tighten tool selection", WORKFLOW_DEFINITION, "replace tool policy");
        var candidate = new Candidate(
                "cand-001",
                mutation.id(),
                "candidate/mut-001",
                Path.of(".worktrees/candidate-mut-001"),
                "abc1234"
        );
        var unexpectedCandidate = new Candidate(
                "cand-002",
                mutation.id(),
                "candidate/mut-002",
                Path.of(".worktrees/candidate-mut-002"),
                "def5678"
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
                        unexpectedCandidate,
                        evidence,
                        Map.of("correctness", 1.0),
                        1.0,
                        DISCARD
                ),
                metadata,
                decisions
        );

        assertThatThrownBy(() -> loop.evaluate(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fitness result candidate does not match evaluated candidate");
        assertThat(metadata.recordedCandidates()).containsExactly(candidate);
        assertThat(metadata.recordedFitness()).isEmpty();
        assertThat(decisions.discardedCandidate()).isEmpty();
        assertThat(decisions.promotedCandidate()).isEmpty();
    }

    @Test
    void stopsInvalidModelProposalBeforeCandidateCreationAndEvaluation() {
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var mutation = new Mutation("mut-unsafe", "mutate outside boundary", WORKFLOW_DEFINITION, "rewrite everything");
        var decisions = new RecordingDecisionSink();
        var metadata = new RecordingMetadataStore();

        var loop = new MutationEvaluationLoop(
                ignored -> mutation,
                (workflow, proposed) -> ValidationResult.invalid("patch exceeds bounded workflow mutation policy"),
                (workflow, proposed) -> {
                    throw new AssertionError("candidate workspace must not run for invalid mutations");
                },
                ignored -> {
                    throw new AssertionError("checks must not run for invalid mutations");
                },
                ignored -> {
                    throw new AssertionError("benchmarks must not run for invalid mutations");
                },
                (candidate, evidence) -> {
                    throw new AssertionError("fitness scoring must not run for invalid mutations");
                },
                metadata,
                decisions
        );

        assertThatThrownBy(() -> loop.evaluate(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mutation validation failed: patch exceeds bounded workflow mutation policy");
        assertThat(metadata.recordedCandidates()).isEmpty();
        assertThat(metadata.recordedFitness()).isEmpty();
        assertThat(decisions.discardedCandidate()).isEmpty();
        assertThat(decisions.promotedCandidate()).isEmpty();
    }

    @Test
    void realValidatorStopsAuthorityBearingProposalBeforeCandidateCreationAndEvaluation() {
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var mutation = new Mutation(
                "promote-candidate",
                "tighten tool selection",
                WORKFLOW_DEFINITION,
                "replace tool policy"
        );
        var decisions = new RecordingDecisionSink();
        var metadata = new RecordingMetadataStore();

        var loop = new MutationEvaluationLoop(
                ignored -> mutation,
                new BoundedMutationValidator(),
                (workflow, proposed) -> {
                    throw new AssertionError("candidate workspace must not run for invalid mutations");
                },
                ignored -> {
                    throw new AssertionError("checks must not run for invalid mutations");
                },
                ignored -> {
                    throw new AssertionError("benchmarks must not run for invalid mutations");
                },
                (candidate, evidence) -> {
                    throw new AssertionError("fitness scoring must not run for invalid mutations");
                },
                metadata,
                decisions
        );

        assertThatThrownBy(() -> loop.evaluate(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "mutation validation failed: "
                                + "mutation must not contain approval, scoring, promotion, discard or rollback authority"
                );
        assertThat(metadata.recordedCandidates()).isEmpty();
        assertThat(metadata.recordedFitness()).isEmpty();
        assertThat(decisions.discardedCandidate()).isEmpty();
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
