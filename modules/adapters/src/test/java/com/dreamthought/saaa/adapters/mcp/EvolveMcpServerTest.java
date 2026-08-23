package com.dreamthought.saaa.adapters.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.evolve.EvolveRunResult;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EvolveMcpServerTest {
    @Test
    void cliFailureDoesNotTerminateTheMcpServer() {
        var calls = new AtomicInteger();
        var server = new EvolveMcpServer(new EvolveMcpTool((request, reporter) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalArgumentException("workflow file not found");
            }
            return successfulRun();
        }, new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(Optional::empty))));

        var failed = server.callEvolve(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));
        var succeeded = server.callEvolve(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(failed.error()).isTrue();
        assertThat(failed.json()).contains("workflow file not found");
        assertThat(succeeded.error()).isFalse();
        assertThat(succeeded.json()).contains("\"commitSha\":\"abc123\"");
    }

    @Test
    void failedToolCallDoesNotPublishSuccessShapedStructuredContent() {
        var server = new EvolveMcpServer(new EvolveMcpTool((request, reporter) -> {
            throw new IllegalArgumentException("workflow file not found");
        }, new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(Optional::empty))));

        McpSchema.CallToolResult result = server.callSdkTool(null, McpSchema.CallToolRequest
                .builder(EvolveMcpServer.TOOL_NAME)
                .arguments(Map.of("targetFolder", "/tmp/repo/toy", "behaviourCases", List.of("workflow-check")))
                .build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent()).isNull();
        assertThat(result.content().toString()).contains("workflow file not found");
    }

    private static EvolveRunResult successfulRun() {
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/toy-mut-001",
                Path.of("/tmp/repo/.worktrees/candidate-toy-mut-001"),
                "abc123");
        var evidence = new EvaluationEvidence(
                List.of(CheckEvidence.passed("workflow-check", "all good")),
                List.of(),
                Instant.parse("2026-08-01T00:00:00Z"));
        var objectives = new LinkedHashMap<String, Double>();
        objectives.put("subject.objective.task_success", 1.0);
        objectives.put("subject.invariant.deterministic_checks", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, FitnessScore.of(0.87, FitnessDecision.PROMOTE));
        return new EvolveRunResult(fitness, Path.of("/tmp/repo/toy/journal.md"));
    }
}
