package com.dreamthought.saaa.adapters.journal;

import com.dreamthought.saaa.deterministic.CandidateDecisionSink;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records candidate branch-pointer decisions without acting on them. The port does not expose a
 * merge operation, so promotion cannot become an automatic merge by adapter configuration.
 */
public final class JournalDecisionSink implements CandidateDecisionSink {
    private final List<String> decisions = new ArrayList<>();

    @Override
    public void recordPromotedCandidateBranch(CandidateBranchRef candidateBranchRef, FitnessResult result) {
        record("PROMOTE", candidateBranchRef, result);
    }

    @Override
    public void discardCandidateBranch(CandidateBranchRef candidateBranchRef, FitnessResult result) {
        record("DISCARD", candidateBranchRef, result);
    }

    public List<String> decisions() {
        return List.copyOf(decisions);
    }

    private void record(String decision, CandidateBranchRef candidateBranchRef, FitnessResult result) {
        Objects.requireNonNull(candidateBranchRef, "candidateBranchRef");
        Objects.requireNonNull(result, "result");
        decisions.add(decision + " " + candidateBranchRef.value() + " " + result.aggregateScore());
    }
}
