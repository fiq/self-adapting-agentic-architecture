package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;

public interface CandidateDecisionSink {
    void promote(Candidate candidate, FitnessResult result);

    void discard(Candidate candidate, FitnessResult result);
}
