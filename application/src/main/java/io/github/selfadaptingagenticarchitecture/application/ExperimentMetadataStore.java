package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;

public interface ExperimentMetadataStore {
    void recordCandidate(Candidate candidate);

    void recordFitness(FitnessResult result);
}
