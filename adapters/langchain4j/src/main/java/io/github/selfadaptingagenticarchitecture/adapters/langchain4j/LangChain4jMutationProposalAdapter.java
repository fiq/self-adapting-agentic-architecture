package io.github.selfadaptingagenticarchitecture.adapters.langchain4j;

import io.github.selfadaptingagenticarchitecture.application.MutationProposer;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;

public final class LangChain4jMutationProposalAdapter implements MutationProposer {
    @Override
    public Mutation proposeFor(WorkflowGraph baseline) {
        throw new UnsupportedOperationException("LangChain4j mutation proposal adapter is pending provider selection");
    }
}
