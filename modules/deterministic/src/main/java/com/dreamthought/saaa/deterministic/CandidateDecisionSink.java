package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.FitnessResult;

public interface CandidateDecisionSink {
    void recordPromotedCandidateBranch(CandidateBranchRef candidateBranchRef, FitnessResult result);

    void discardCandidateBranch(CandidateBranchRef candidateBranchRef, FitnessResult result);
}
