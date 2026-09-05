package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationProposalRequest;

@FunctionalInterface
public interface CandidateEvaluator {
    FitnessResult evaluate(MutationProposalRequest request);
}
