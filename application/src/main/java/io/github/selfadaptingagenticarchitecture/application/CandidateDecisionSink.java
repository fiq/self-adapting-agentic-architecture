package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;

public interface CandidateDecisionSink {
    void promote(Candidate candidate, FitnessResult result);

    void discard(Candidate candidate, FitnessResult result);
}
