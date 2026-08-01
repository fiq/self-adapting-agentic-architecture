package com.dreamthought.saaa.adapters.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.evolve.EvolveRunResult;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EvolveMcpToolTest {
    @Test
    void returnsAStructuredFitnessResultRatherThanText() {
        var tool = toolReturning(successfulRun());

        var response = tool.call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.error()).isFalse();
        assertThat(response.json())
                .startsWith("{\"candidate\":")
                .contains(
                        "\"commitSha\":\"abc123\"",
                        "\"objectives\":{",
                        "\"aggregateScore\":0.87",
                        "\"aggregateScoreDisplay\":\"0.87\"",
                        "\"decision\":\"PROMOTE\"",
                        "\"journalPath\":\"/tmp/repo/toy/journal.md\"");
    }

    @Test
    void gateOutcomesAppearAfterMeasuredObjectiveScoresInTheSerialisedResponse() {
        var response = toolReturning(successfulRun()).call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.json().indexOf("\"task_success\""))
                .isLessThan(response.json().indexOf("\"hard_gate_deterministic_checks\""));
        assertThat(response.json().indexOf("\"parsimony\""))
                .isLessThan(response.json().indexOf("\"hard_gate_non_empty_realization\""));
    }

    @Test
    void rejectsAnInputFieldThatWouldForceAPromotionOrOverrideAGate() {
        assertThatThrownBy(() -> EvolveMcpRequest.fromArguments(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check"),
                "forcePromotion", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forcePromotion");

        assertThatThrownBy(() -> EvolveMcpRequest.fromArguments(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check"),
                "hard_gate_deterministic_checks", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hard_gate_deterministic_checks");
    }

    @Test
    void mcpInputCannotEnableAnAutoMerge() {
        assertThat(EvolveMcpRequest.inputSchema())
                .containsEntry("additionalProperties", false);
        assertThatThrownBy(() -> EvolveMcpRequest.fromArguments(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check"),
                "autoMerge", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autoMerge");
    }

    @Test
    void mcpInputCannotTransportCredentials() {
        assertThatThrownBy(() -> EvolveMcpRequest.fromArguments(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check"),
                "apiKey", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void mcpResponseNeverContainsTheApiKeyEvenWhenTheProposerErrorMessageDoes() {
        var serializer = new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(
                () -> Optional.of("test-secret")));
        var tool = new EvolveMcpTool((request, reporter) -> {
            throw new IllegalStateException(
                    "provider said Authorization: Bearer test-secret and echoed test-secret");
        }, serializer);

        var response = tool.call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.error()).isTrue();
        assertThat(response.json())
                .contains("<redacted>")
                .doesNotContain("test-secret")
                .doesNotContain("Authorization: Bearer test-secret");
    }

    @Test
    void cappedResponseStillReturnsValidJson() {
        String longSummary = "x".repeat(EvolveMcpResponseSerializer.RESPONSE_LIMIT);
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/toy-mut-001",
                Path.of("/tmp/repo/.worktrees/candidate-toy-mut-001"),
                "abc123");
        var checks = new java.util.ArrayList<CheckEvidence>();
        for (int index = 0; index < 12; index++) {
            checks.add(CheckEvidence.passed("workflow-check-" + index, longSummary));
        }
        var evidence = new EvaluationEvidence(checks, List.of(), Instant.parse("2026-08-01T00:00:00Z"));
        var objectives = new LinkedHashMap<String, Double>();
        objectives.put("task_success", 1.0);
        objectives.put("hard_gate_deterministic_checks", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, 0.87, FitnessDecision.PROMOTE);
        var response = toolReturning(new EvolveRunResult(fitness, Path.of("/tmp/repo/toy/journal.md")))
                .call(Map.of(
                        "targetFolder", "/tmp/repo/toy",
                        "behaviourCases", List.of("workflow-check")));

        assertThat(response.error()).isFalse();
        assertThat(response.json())
                .hasSizeLessThanOrEqualTo(EvolveMcpResponseSerializer.RESPONSE_LIMIT)
                .startsWith("{")
                .endsWith("}")
                .contains("\"truncated\":true")
                .contains("\"journalPath\":\"/tmp/repo/toy/journal.md\"");
    }

    @Test
    void outputSchemaDescribesEvidenceAndIsClosed() {
        var schema = new EvolveMcpServer(toolReturning(successfulRun())).toolDefinition().outputSchema();

        assertThat(schema).containsEntry("additionalProperties", false);
        assertThat(schema.get("properties").toString())
                .contains("evidence")
                .contains("checks")
                .contains("benchmarks")
                .contains("additionalProperties=false");
    }

    private static EvolveMcpTool toolReturning(EvolveRunResult result) {
        return new EvolveMcpTool((request, reporter) -> result,
                new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(Optional::empty)));
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
        objectives.put("task_success", 1.0);
        objectives.put("parsimony", 0.9);
        objectives.put("hard_gate_deterministic_checks", 1.0);
        objectives.put("hard_gate_non_empty_realization", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, 0.87, FitnessDecision.PROMOTE);
        return new EvolveRunResult(fitness, Path.of("/tmp/repo/toy/journal.md"));
    }
}
