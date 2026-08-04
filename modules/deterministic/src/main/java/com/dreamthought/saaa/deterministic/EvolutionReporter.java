package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.RetrievalBundle;

/**
 * Progress events from one evaluation. The loop reports; it never prints. That keeps a terminal, a
 * live session view and a remote caller as equal consumers of the same run.
 */
public interface EvolutionReporter {
    EvolutionReporter NO_OP = new EvolutionReporter() { };

    default void proposed(Mutation mutation) { }

    default void retrievalPrepared(RetrievalBundle retrieval) { }

    default void candidateCreated(Candidate candidate) { }

    default void evidenceCollected(EvaluationEvidence evidence) { }

    default void scored(FitnessResult result) { }
}
