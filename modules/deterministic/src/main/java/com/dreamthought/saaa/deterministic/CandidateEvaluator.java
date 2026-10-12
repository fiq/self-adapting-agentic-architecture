package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.MutationProposalRequest;

@FunctionalInterface
public interface CandidateEvaluator {
    CandidateEvaluation evaluate(MutationProposalRequest request);
}
