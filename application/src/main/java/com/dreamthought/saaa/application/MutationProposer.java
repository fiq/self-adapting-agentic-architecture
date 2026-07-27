package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Mutation;
import com.dreamthought.saaa.core.WorkflowGraph;

@FunctionalInterface
public interface MutationProposer {
    Mutation proposeFor(WorkflowGraph baseline);
}
