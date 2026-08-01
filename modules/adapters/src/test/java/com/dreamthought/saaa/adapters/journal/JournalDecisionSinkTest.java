package com.dreamthought.saaa.adapters.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JournalDecisionSinkTest {
    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void recordsBothOutcomesInOrder() {
        var sink = new JournalDecisionSink();

        sink.recordPromotedCandidateBranch(CandidateBranchRef.fromCandidate(CANDIDATE), result(FitnessDecision.PROMOTE, 0.87));
        sink.discardCandidateBranch(CandidateBranchRef.fromCandidate(CANDIDATE), result(FitnessDecision.DISCARD, 0.10));

        assertThat(sink.decisions()).containsExactly(
                "PROMOTE refs/heads/candidate/toy-MUT-1 0.87",
                "DISCARD refs/heads/candidate/toy-MUT-1 0.1");
    }

    private static FitnessResult result(FitnessDecision decision, double score) {
        var evidence = new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-07-28T00:00:00Z"));
        return new FitnessResult(CANDIDATE, evidence, Map.of(), score, decision);
    }
}
