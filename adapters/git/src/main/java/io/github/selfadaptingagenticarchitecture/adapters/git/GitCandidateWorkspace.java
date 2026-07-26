package io.github.selfadaptingagenticarchitecture.adapters.git;

import io.github.selfadaptingagenticarchitecture.application.CandidateWorkspace;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;

public final class GitCandidateWorkspace implements CandidateWorkspace {
    @Override
    public Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation) {
        throw new UnsupportedOperationException("Git worktree candidate creation is pending implementation");
    }
}
