package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MutationEvaluationLoopReportingTest {
    @Test
    void aCandidatesObservedUsageReachesTheStoreAndTheReporters() {
        var usages = List.of(
                new AgentUsage(OptionalLong.of(11), OptionalLong.of(12), Optional.of(new BigDecimal("1.25")),
                        OptionalLong.of(13), OptionalInt.of(1),
                        Optional.of(new com.dreamthought.saaa.domain.PriceIdentity("provider-a", "model-a"))),
                new AgentUsage(OptionalLong.of(21), OptionalLong.of(22), Optional.of(new BigDecimal("2.50")),
                        OptionalLong.of(23), OptionalInt.of(2),
                        Optional.of(new com.dreamthought.saaa.domain.PriceIdentity("provider-b", "model-b"))));
        var proposal = new AtomicInteger();
        var candidateNumber = new AtomicInteger();
        var recordedUsages = new ArrayList<String>();
        var reportedUsages = new ArrayList<String>();

        MutationProposer proposer = new MutationProposer() {
            @Override
            public Mutation proposeFor(WorkflowGraph baseline) {
                int number = proposal.getAndIncrement();
                return new Mutation("MUT-" + number, "tighten guard", MutationScope.WORKFLOW_DEFINITION,
                        "new content " + number);
            }

            @Override
            public AgentUsage usage() {
                return usages.get(proposal.get() - 1);
            }
        };
        EvolutionReporter reporter = new EvolutionReporter() {
            @Override
            public void usageObserved(Candidate candidate, AgentUsage usage) {
                reportedUsages.add(candidate.id() + ":" + usage.inputTokens().orElseThrow());
            }
        };
        var loop = new MutationEvaluationLoop(
                proposer,
                (workflow, proposed) -> ValidationResult.passed(),
                (workflow, proposed) -> {
                    int number = candidateNumber.getAndIncrement();
                    return new Candidate("candidate-" + number, proposed.id(), "candidate/toy-" + number,
                            Path.of(".worktrees/c" + number), "abc123" + number);
                },
                ignored -> List.of(CheckEvidence.passed("workflow-check", "ok")),
                ignored -> List.of(),
                MutationEvaluationLoopReportingTest::discarded,
                new ExperimentMetadataStore() {
                    @Override
                    public void recordCandidate(Candidate recorded) { }

                    @Override
                    public void recordFitness(FitnessResult recorded) { }

                    @Override
                    public void recordUsage(Candidate recorded, AgentUsage usage) {
                        recordedUsages.add(recorded.id() + ":" + usage.inputTokens().orElseThrow());
                    }

                    @Override
                    public void recordGeneration(com.dreamthought.saaa.domain.GenerationRecord record) {
                        throw new AssertionError("a single-candidate run has no generation to record");
                    }
                },
                new CandidateDecisionSink() {
                    @Override
                    public void recordPromotedCandidateBranch(CandidateBranchRef branch, FitnessResult result) { }

                    @Override
                    public void recordDiscardedCandidateBranch(CandidateBranchRef branch, FitnessResult result) { }
                },
                reporter,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        var baseline = new WorkflowGraph("toy", "v1", "old content");
        loop.evaluate(baseline);
        loop.evaluate(baseline);

        assertThat(recordedUsages).containsExactly("candidate-0:11", "candidate-1:21");
        assertThat(reportedUsages).containsExactly("candidate-0:11", "candidate-1:21");
    }

    @Test
    void reportsEveryStageThroughThePort() {
        List<String> events = new ArrayList<>();
        EvolutionReporter reporter = new EvolutionReporter() {
            @Override
            public void proposed(Mutation mutation) {
                events.add("proposed:" + mutation.id());
            }

            @Override
            public void candidateCreated(Candidate candidate) {
                events.add("candidate:" + candidate.id());
            }

            @Override
            public void evidenceCollected(EvaluationEvidence evidence) {
                events.add("evidence:" + evidence.checks().size());
            }

            @Override
            public void scored(FitnessResult result) {
                events.add("scored:" + result.decision());
            }
        };

        var baseline = new WorkflowGraph("toy", "v1", "old content");
        var mutation = new Mutation("MUT-1", "tighten guard", MutationScope.WORKFLOW_DEFINITION, "new content");
        var candidate = new Candidate(
                "candidate-MUT-1", "MUT-1", "candidate/toy-MUT-1", Path.of(".worktrees/c"), "abc1234");

        var loop = new MutationEvaluationLoop(
                ignored -> mutation,
                (workflow, proposed) -> ValidationResult.passed(),
                (workflow, proposed) -> candidate,
                ignored -> List.of(CheckEvidence.passed("workflow-check", "ok")),
                ignored -> List.of(),
                (evaluated, evidence, contract) -> new FitnessResult(
                        evaluated, evidence, Map.of(), com.dreamthought.saaa.domain.FitnessScore.of(0.10, FitnessDecision.DISCARD),
                        new com.dreamthought.saaa.domain.ScoringContext(
                                List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                                java.util.Set.of(), java.util.Set.of(), 0.80,
                                java.util.Set.of("case"), 80, java.util.Map.of())),
                new ExperimentMetadataStore() {
                    @Override
                    public void recordCandidate(Candidate recorded) { }

                    @Override
                    public void recordFitness(FitnessResult recorded) { }

                    @Override
                    public void recordUsage(Candidate recorded, com.dreamthought.saaa.domain.AgentUsage usage) { }

                    @Override
                    public void recordGeneration(
                            com.dreamthought.saaa.domain.GenerationRecord record) {
                        throw new AssertionError("a single-candidate run has no generation to record");
                    }
                },
                new CandidateDecisionSink() {
                    @Override
                    public void recordPromotedCandidateBranch(
                            CandidateBranchRef candidateBranchRef,
                            FitnessResult result
                    ) { }

                    @Override
                    public void recordDiscardedCandidateBranch(
                            CandidateBranchRef candidateBranchRef,
                            FitnessResult result
                    ) { }
                },
                reporter,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        loop.evaluate(baseline);

        assertThat(events).containsExactly(
                "proposed:MUT-1",
                "candidate:candidate-MUT-1",
                "evidence:1",
                "scored:DISCARD");
    }

    private static FitnessResult discarded(Candidate candidate, EvaluationEvidence evidence, Optional<com.dreamthought.saaa.domain.MutationContract> contract) {
        return new FitnessResult(candidate, evidence, Map.of(),
                com.dreamthought.saaa.domain.FitnessScore.of(0.10, FitnessDecision.DISCARD),
                new com.dreamthought.saaa.domain.ScoringContext(
                        List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                        java.util.Set.of(), java.util.Set.of(), 0.80,
                        java.util.Set.of("case"), 80, Map.of()));
    }
}
