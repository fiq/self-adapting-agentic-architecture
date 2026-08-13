package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void routesPreparedRetrievalThroughAnAgentHarnessBeforeDeterministicEvaluation(@TempDir Path dir)
            throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve("workflow-check.sh"), "#!/usr/bin/env bash\ngrep -q 'enforce' \"$(dirname \"$0\")/workflow.txt\"\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        var observedRequest = new AtomicReference<com.dreamthought.saaa.domain.AgentRequest>();
        AgentHarness harness = request -> {
            observedRequest.set(request);
            return new AgentRunResult(
                    AgentRunStatus.COMPLETED,
                    request.route(),
                    Optional.of(new Mutation("harness-change", "enforce the check", MutationScope.WORKFLOW_DEFINITION,
                            "draft-check: enforce\n")),
                    Optional.of("session-1"), Optional.empty(), AgentUsage.none(), Optional.empty());
        };
        var registry = new ProposerProfileRegistry(
                ignored -> ignoredBaselineProposer(),
                ignored -> harness);

        var result = new EvolveRunner(
                registry,
                java.time.Clock.systemUTC(),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvidenceRetriever.none("test-retrieval"),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore.disabled())
                .run(new EvolveRunRequest(target, "acp", "workflow.txt", List.of("workflow-check"), 20),
                        EvolutionReporter.NO_OP);

        assertThat(result.fitnessResult().decision()).isEqualTo(com.dreamthought.saaa.domain.FitnessDecision.PROMOTE);
        assertThat(observedRequest.get()).isNotNull();
        assertThat(observedRequest.get().retrieval()).isPresent();
        assertThat(observedRequest.get().retrieval().orElseThrow().configurationId()).isEqualTo("test-retrieval");
    }

    private static com.dreamthought.saaa.deterministic.MutationProposer ignoredBaselineProposer() {
        return baseline -> new Mutation("unused", "unused", MutationScope.WORKFLOW_DEFINITION, baseline.definition());
    }

    private static void git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git command failed: " + command);
        }
    }
}
