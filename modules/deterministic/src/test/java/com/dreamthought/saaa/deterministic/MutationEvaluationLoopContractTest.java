package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.MutationTarget;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * CHG-019 S6. What gated the candidate must be what the operator declared, so a contract given to
 * the loop has to reach the scorer as the same instance, and a run that declared nothing must not
 * invent one to satisfy the widened port.
 */
final class MutationEvaluationLoopContractTest {
    @Test
    void carriesTheDeclaredContractToTheScorer() {
        var contract = ContractFixture.declaring("regression_case_added");
        var seen = new AtomicReference<Optional<MutationContract>>();

        loopSupplying(contract, seen).evaluate(new WorkflowGraph("toy", "v1", "old content"));

        assertThat(seen.get())
                .as("the scorer receives the contract the loop was given, not a rebuilt copy")
                .containsSame(contract);
    }

    @Test
    void aRunWithoutAContractSuppliesNone() {
        var seen = new AtomicReference<Optional<MutationContract>>();

        loopSupplying(null, seen).evaluate(new WorkflowGraph("toy", "v1", "old content"));

        assertThat(seen.get())
                .as("a run that declared nothing must not invent a contract to satisfy the port")
                .isEmpty();
    }

    private static MutationEvaluationLoop loopSupplying(
            MutationContract contract, AtomicReference<Optional<MutationContract>> seen) {
        var mutation = new Mutation("MUT-1", "tighten guard", MutationScope.WORKFLOW_DEFINITION, "new content");
        var candidate = new Candidate(
                "candidate-MUT-1", "MUT-1", "candidate/toy-MUT-1", Path.of(".worktrees/c"), "abc1234");

        return new MutationEvaluationLoop(
                ignored -> mutation,
                (workflow, proposed) -> ValidationResult.passed(),
                (workflow, proposed) -> candidate,
                ignored -> List.of(CheckEvidence.passed("regression_case_added", "ok")),
                ignored -> List.of(),
                (evaluated, evidence, suppliedContract) -> {
                    seen.set(suppliedContract);
                    return new FitnessResult(
                            evaluated, evidence, Map.of(), com.dreamthought.saaa.domain.FitnessScore.of(0.10, FitnessDecision.DISCARD),
                            new com.dreamthought.saaa.domain.ScoringContext(
                                    List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                                    java.util.Set.of(), java.util.Set.of(), 0.80,
                        java.util.Set.of("case"), 80, java.util.Map.of()));
                },
                new ExperimentMetadataStore() {
                    @Override
                    public void recordCandidate(Candidate recorded) { }

                    @Override
                    public void recordFitness(FitnessResult recorded) { }
                },
                new CandidateDecisionSink() {
                    @Override
                    public void recordPromotedCandidateBranch(
                            CandidateBranchRef candidateBranchRef, FitnessResult result) { }

                    @Override
                    public void recordDiscardedCandidateBranch(
                            CandidateBranchRef candidateBranchRef, FitnessResult result) { }
                },
                Optional.ofNullable(contract),
                java.time.Clock.systemUTC());
    }


}
