package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;

@FunctionalInterface
public interface CandidateWorkspace {
    Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation);
}
