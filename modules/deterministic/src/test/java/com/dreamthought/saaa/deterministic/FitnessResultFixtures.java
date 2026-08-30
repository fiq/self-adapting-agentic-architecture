package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.ScoringContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scored candidates for the tests that rank them. Shared because two suites now need them. */
final class FitnessResultFixtures {
    static final List<FitnessObjective> OBJECTIVES =
            List.of(new FitnessObjective("subject.objective.task_success", 1.00));

    private FitnessResultFixtures() {
    }

    static FitnessResult result(String candidateId, double magnitude, FitnessDecision decision) {
        return result(candidateId, magnitude, decision, context(0.80));
    }

    static FitnessResult result(
            String candidateId, double magnitude, FitnessDecision decision, ScoringContext context) {
        return new FitnessResult(
                new Candidate(candidateId, "MUT-" + candidateId, "candidate/" + candidateId,
                        Path.of(".worktrees", candidateId), "0".repeat(40)),
                new EvaluationEvidence(List.<CheckEvidence>of(), List.of(), Instant.EPOCH),
                Map.of("subject.objective.task_success", magnitude),
                FitnessScore.of(magnitude, decision),
                context);
    }

    static ScoringContext context(double promotionThreshold) {
        return new ScoringContext(
                OBJECTIVES, Set.of(), Set.of(), promotionThreshold, Set.of("case_a"), 80,
                Map.<String, Double>of());
    }
}
