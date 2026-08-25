package com.dreamthought.saaa.adapters.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JournalDecisionSinkTest {
    /** Any scoring context; these tests assert reporting and transport, not comparability. */
    private static final com.dreamthought.saaa.domain.ScoringContext TEST_SCORING_CONTEXT =
            new com.dreamthought.saaa.domain.ScoringContext(
                    java.util.List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                    java.util.Set.of(), java.util.Set.of(), 0.80,
                    java.util.Set.of("case"), 80, java.util.Map.of());

    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void recordsBothOutcomesInOrder() {
        var sink = new JournalDecisionSink();

        sink.recordPromotedCandidateBranch(CandidateBranchRef.fromCandidate(CANDIDATE), result(FitnessDecision.PROMOTE, 0.87));
        sink.recordDiscardedCandidateBranch(CandidateBranchRef.fromCandidate(CANDIDATE), result(FitnessDecision.DISCARD, 0.10));

        assertThat(sink.decisions()).containsExactly(
                "PROMOTE refs/heads/candidate/toy-MUT-1 0.87",
                "DISCARD refs/heads/candidate/toy-MUT-1 0.1");
    }

    private static FitnessResult result(FitnessDecision decision, double score) {
        var evidence = new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-07-28T00:00:00Z"));
        return new FitnessResult(CANDIDATE, evidence, Map.of(), FitnessScore.of(score, decision), TEST_SCORING_CONTEXT);
    }
}
