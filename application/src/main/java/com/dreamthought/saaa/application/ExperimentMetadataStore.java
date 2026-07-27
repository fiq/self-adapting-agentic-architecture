package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.FitnessResult;

public interface ExperimentMetadataStore {
    void recordCandidate(Candidate candidate);

    void recordFitness(FitnessResult result);
}
