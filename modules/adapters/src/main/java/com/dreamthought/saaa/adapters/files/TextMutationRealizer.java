package com.dreamthought.saaa.adapters.files;

import com.dreamthought.saaa.deterministic.MutationRealizer;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Whole-file realization: the mutation patch becomes the entire new content of one file.
 * Hunk-based and AST-aware realization are out of scope; the operator model treats the resulting
 * diff as realization evidence rather than as the mutation itself.
 */
public final class TextMutationRealizer implements MutationRealizer {
    private final String relativeWorkflowPath;

    public TextMutationRealizer(String relativeWorkflowPath) {
        this.relativeWorkflowPath = Objects.requireNonNull(relativeWorkflowPath, "relativeWorkflowPath");
        if (relativeWorkflowPath.isBlank()) {
            throw new IllegalArgumentException("relativeWorkflowPath must not be blank");
        }
    }

    @Override
    public void realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(worktreePath, "worktreePath");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");

        Path root = worktreePath.toAbsolutePath().normalize();
        Path target = root.resolve(relativeWorkflowPath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                    "workflow path must stay inside the candidate worktree: " + relativeWorkflowPath);
        }
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("workflow file not found in candidate worktree: " + relativeWorkflowPath);
        }
        try {
            Files.writeString(target, mutation.patch());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to realize mutation into " + relativeWorkflowPath, exception);
        }
    }
}
