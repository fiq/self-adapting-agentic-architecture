package io.github.selfadaptingagenticarchitecture.core;

import java.nio.file.Path;
import java.util.Objects;

public record Candidate(String id, String mutationId, String branchName, Path worktreePath, String commitSha) {
    public Candidate {
        id = Require.nonBlank(id, "id");
        mutationId = Require.nonBlank(mutationId, "mutationId");
        branchName = Require.nonBlank(branchName, "branchName");
        worktreePath = Objects.requireNonNull(worktreePath, "worktreePath");
        commitSha = Require.nonBlank(commitSha, "commitSha");
    }
}
