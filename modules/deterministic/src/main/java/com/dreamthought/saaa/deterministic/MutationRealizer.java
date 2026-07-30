package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;

/**
 * Applies a mutation to files inside a candidate worktree. The realizer decides what changes; the
 * workspace adapter decides how the change is committed.
 */
@FunctionalInterface
public interface MutationRealizer {
    void realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation);
}
