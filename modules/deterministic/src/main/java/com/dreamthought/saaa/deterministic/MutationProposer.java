package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;

@FunctionalInterface
public interface MutationProposer {
    Mutation proposeFor(WorkflowGraph baseline);
}
