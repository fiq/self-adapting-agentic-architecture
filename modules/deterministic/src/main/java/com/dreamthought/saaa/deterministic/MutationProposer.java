package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.PreparedMutationProposalRequest;
import com.dreamthought.saaa.domain.ProposerEvidence;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Optional;

@FunctionalInterface
public interface MutationProposer {
    Mutation proposeFor(WorkflowGraph baseline);

    default Mutation proposeFor(PreparedMutationProposalRequest request) {
        return proposeFor(request.baseline());
    }

    /**
     * The {@code variant}-th proposal of a generation, one-based.
     *
     * <p>The default ignores the variant and proposes exactly as it always did, so N variants from a
     * proposer with no notion of variety are N ordinary calls. For a deterministic proposer that is
     * one candidate N times, which reads like a population and is not one.
     *
     * <p>That default is safe only because nothing relies on it being enough:
     * {@code GenerationEvaluationLoop} checks that a generation's candidates actually carry distinct
     * mutations and fails the run when they do not. A proposer that cannot vary is therefore reported
     * rather than quietly evaluated N times.
     */
    default Mutation proposeFor(PreparedMutationProposalRequest request, int variant) {
        if (variant < 1) {
            throw new IllegalArgumentException("variant is one-based, got " + variant);
        }
        return proposeFor(request);
    }

    default Optional<ProposerEvidence> proposerEvidence() {
        return Optional.empty();
    }
}
