package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.RankedGeneration;
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

    /** Usage observed while proposing this candidate. */
    default void usageObserved(Candidate candidate, AgentUsage usage) { }

    default void scored(FitnessResult result) { }

    /**
     * A generation stopped before it could be ranked. The count is the number of candidates that
     * had produced evidence when the aborting condition was found.
     */
    default void generationAborted(int requestedCount, int evaluatedCount, String reason) { }

    /**
     * One generation finished and was ranked. Never fired by a single-candidate run, which has
     * nothing to rank.
     */
    default void generationRanked(RankedGeneration generation) { }
}
