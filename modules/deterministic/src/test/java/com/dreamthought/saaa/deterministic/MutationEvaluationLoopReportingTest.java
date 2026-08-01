package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MutationEvaluationLoopReportingTest {
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
                (evaluated, evidence) -> new FitnessResult(
                        evaluated, evidence, Map.of(), 0.10, FitnessDecision.DISCARD),
                new ExperimentMetadataStore() {
                    @Override
                    public void recordCandidate(Candidate recorded) { }

                    @Override
                    public void recordFitness(FitnessResult recorded) { }
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
}
