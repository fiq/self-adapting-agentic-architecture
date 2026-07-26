package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;

@FunctionalInterface
public interface MutationProposer {
    Mutation proposeFor(WorkflowGraph baseline);
}
