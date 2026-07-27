package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;

public interface ExperimentMetadataStore {
    void recordCandidate(Candidate candidate);

    void recordFitness(FitnessResult result);
}
