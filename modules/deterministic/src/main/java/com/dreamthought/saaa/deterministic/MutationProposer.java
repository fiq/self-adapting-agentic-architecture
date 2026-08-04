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

    default Optional<ProposerEvidence> proposerEvidence() {
        return Optional.empty();
    }
}
