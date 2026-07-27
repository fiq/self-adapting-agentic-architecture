package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.Mutation;
import com.dreamthought.saaa.core.WorkflowGraph;

@FunctionalInterface
public interface CandidateWorkspace {
    Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation);
}
