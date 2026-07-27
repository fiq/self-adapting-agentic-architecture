package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.FitnessResult;

public interface CandidateDecisionSink {
    void promote(Candidate candidate, FitnessResult result);

    void discard(Candidate candidate, FitnessResult result);
}
