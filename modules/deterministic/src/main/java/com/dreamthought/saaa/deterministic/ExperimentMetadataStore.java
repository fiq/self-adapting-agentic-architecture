package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.GenerationRecord;

public interface ExperimentMetadataStore {
    void recordCandidate(Candidate candidate);

    void recordFitness(FitnessResult result);

    /**
     * Records one generation's ranking, winner, shared fingerprint, evidence count and spread.
     *
     * <p>Abstract rather than a default no-op on purpose. A store that silently does nothing here
     * would let a generation run, report a winner and leave no trace of how it was selected, and the
     * test that checked the record would pass against a store that never wrote one.
     */
    void recordGeneration(GenerationRecord record);
}
