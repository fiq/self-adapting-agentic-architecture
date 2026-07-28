package com.dreamthought.saaa.adapters.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TextMutationRealizerTest {
    private final WorkflowGraph baseline = new WorkflowGraph("toy", "v1", "old content");

    @Test
    void writesThePatchAsTheWholeNewFileContent(@TempDir Path worktree) throws IOException {
        Files.createDirectories(worktree.resolve("fixtures/toy"));
        Files.writeString(worktree.resolve("fixtures/toy/workflow.txt"), "old content");

        new TextMutationRealizer("fixtures/toy/workflow.txt")
                .realize(worktree, baseline, mutation("new content"));

        assertThat(worktree.resolve("fixtures/toy/workflow.txt")).hasContent("new content");
    }

    @Test
    void refusesAPathThatEscapesTheWorktree(@TempDir Path worktree) {
        assertThatThrownBy(() -> new TextMutationRealizer("../outside.txt")
                .realize(worktree, baseline, mutation("new content")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must stay inside the candidate worktree");
    }

    @Test
    void failsWhenTheTargetFileDoesNotExistInTheWorktree(@TempDir Path worktree) {
        assertThatThrownBy(() -> new TextMutationRealizer("missing.txt")
                .realize(worktree, baseline, mutation("new content")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing.txt");
    }

    private static Mutation mutation(String patch) {
        return new Mutation("MUT-1", "a summary", MutationScope.WORKFLOW_DEFINITION, patch);
    }
}
