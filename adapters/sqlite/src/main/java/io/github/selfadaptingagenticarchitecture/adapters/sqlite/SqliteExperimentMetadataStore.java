package io.github.selfadaptingagenticarchitecture.adapters.sqlite;

import io.github.selfadaptingagenticarchitecture.application.ExperimentMetadataStore;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;

public final class SqliteExperimentMetadataStore implements ExperimentMetadataStore {
    @Override
    public void recordCandidate(Candidate candidate) {
        throw new UnsupportedOperationException("SQLite candidate metadata persistence is pending migration design");
    }

    @Override
    public void recordFitness(FitnessResult result) {
        throw new UnsupportedOperationException("SQLite fitness metadata persistence is pending migration design");
    }
}
