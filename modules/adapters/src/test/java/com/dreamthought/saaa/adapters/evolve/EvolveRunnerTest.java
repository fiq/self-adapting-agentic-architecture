package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EvolveRunnerTest {
    @Test
    void refusesWorkflowFileSymlinkBeforeReadingBaseline(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve(".git"));
        Path target = Files.createDirectory(dir.resolve("toy"));
        Path outside = Files.writeString(dir.resolve("secrets.env"), "SAAA_MODEL_API_KEY=sk-super-secret");
        Files.createSymbolicLink(target.resolve("workflow.txt"), outside);

        var request = new EvolveRunRequest(target, "fixture", "workflow.txt", List.of("workflow-check"), 80);

        assertThatThrownBy(() -> new EvolveRunner().run(request, EvolutionReporter.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowFile")
                .hasMessageContaining("symlink");
    }
}
