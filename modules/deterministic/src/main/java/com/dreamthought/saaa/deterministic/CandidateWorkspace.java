package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;

@FunctionalInterface
public interface CandidateWorkspace {
    Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation);
}
