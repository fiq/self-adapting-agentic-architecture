package com.dreamthought.saaa.adapters.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.evolve.EvolveRunResult;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class EvolveMcpToolTest {
    /** Any scoring context; these tests assert reporting and transport, not comparability. */
    private static final com.dreamthought.saaa.domain.ScoringContext TEST_SCORING_CONTEXT =
            new com.dreamthought.saaa.domain.ScoringContext(
                    java.util.List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                    java.util.Set.of(), java.util.Set.of(), 0.80);

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
                        "\"fitnessScore\":{\"rawMagnitude\":0.87,\"decision\":\"PROMOTE\"}",
                        "\"journalPath\":\"/tmp/repo/toy/journal.md\"");
    }

    @Test
    void gateOutcomesAppearAfterMeasuredObjectiveScoresInTheSerialisedResponse() {
        var response = toolReturning(successfulRun()).call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.json().indexOf("\"subject.objective.task_success\""))
                .isLessThan(response.json().indexOf("\"subject.invariant.deterministic_checks\""));
        assertThat(response.json().indexOf("\"subject.objective.parsimony\""))
                .isLessThan(response.json().indexOf("\"subject.invariant.non_empty_realization\""));
    }

    @Test
    void objectivesAreSerialisedInStableNameOrderInsideMeasuredAndGateGroups() {
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/toy-mut-001",
                Path.of("/tmp/repo/.worktrees/candidate-toy-mut-001"),
                "abc123");
        var evidence = new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-08-01T00:00:00Z"));
        var objectives = new HashMap<String, Double>();
        objectives.put("subject.objective.task_success", 1.0);
        objectives.put("subject.invariant.z_last", 1.0);
        objectives.put("subject.objective.parsimony", 0.9);
        objectives.put("subject.invariant.a_first", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT);

        var response = toolReturning(new EvolveRunResult(fitness, Path.of("/tmp/repo/toy/journal.md")))
                .call(Map.of(
                        "targetFolder", "/tmp/repo/toy",
                        "behaviourCases", List.of("workflow-check")));

        assertThat(response.json())
                .contains("\"objectives\":{\"subject.objective.parsimony\":0.9,\"subject.objective.task_success\":1.0,"
                        + "\"subject.invariant.a_first\":1.0,\"subject.invariant.z_last\":1.0}");
    }

    @Test
    void legacyKeysAreReEmittedInCanonicalForm() {
        var response = toolReturning(legacyKeyRun()).call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.json())
                .contains("\"subject.objective.task_success\":1.0")
                .contains("\"subject.invariant.deterministic_checks\":1.0")
                .doesNotContain("hard_gate_deterministic_checks")
                .doesNotContain("\"task_success\"");
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
                "subject.invariant.deterministic_checks", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject.invariant.deterministic_checks");
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
    void mcpWorkflowFileCannotEscapeTheTargetFolderOrReachTheRunner() {
        var runnerCalled = new AtomicBoolean();
        var tool = new EvolveMcpTool((request, reporter) -> {
            runnerCalled.set(true);
            return successfulRun();
        }, new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(Optional::empty)));

        var response = tool.call(Map.of(
                "targetFolder", "/tmp/repo/toy",
                "workflowFile", "../secrets.env",
                "behaviourCases", List.of("workflow-check")));

        assertThat(response.error()).isTrue();
        assertThat(runnerCalled.get()).isFalse();
        assertThat(response.json()).contains("workflowFile").doesNotContain("secrets.env");
    }

    @Test
    void cappedErrorResponseStillReturnsValidJson() throws Exception {
        var serializer = new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber(Optional::empty));
        String escapedMessage = "\\".repeat(EvolveMcpResponseSerializer.RESPONSE_LIMIT);

        String response = serializer.serializeError(new IllegalStateException(escapedMessage));

        assertThat(response)
                .hasSizeLessThanOrEqualTo(EvolveMcpResponseSerializer.RESPONSE_LIMIT)
                .startsWith("{")
                .endsWith("}");
        assertThat(McpJsonDefaults.getMapper()
                .readValue(response, new TypeRef<Map<String, Object>>() {})
                .containsKey("error")).isTrue();
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
        objectives.put("subject.objective.task_success", 1.0);
        objectives.put("subject.invariant.deterministic_checks", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT);
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

        var properties = (Map<?, ?>) schema.get("properties");
        var evidence = (Map<?, ?>) properties.get("evidence");
        var evidenceProperties = (Map<?, ?>) evidence.get("properties");
        var checks = (Map<?, ?>) evidenceProperties.get("checks");
        var checkItems = (Map<?, ?>) checks.get("items");
        var checkProperties = (Map<?, ?>) checkItems.get("properties");
        var status = (Map<?, ?>) checkProperties.get("status");
        assertThat(status.get("enum"))
                .isEqualTo(List.of("PASSED", "FAILED", "TIMED_OUT"));
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
        objectives.put("subject.objective.task_success", 1.0);
        objectives.put("subject.objective.parsimony", 0.9);
        objectives.put("subject.invariant.deterministic_checks", 1.0);
        objectives.put("subject.invariant.non_empty_realization", 1.0);
        var fitness = new FitnessResult(candidate, evidence, objectives, FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT);
        return new EvolveRunResult(fitness, Path.of("/tmp/repo/toy/journal.md"));
    }

    private static EvolveRunResult legacyKeyRun() {
        var candidate = new Candidate(
                "candidate-mut-legacy",
                "mut-legacy",
                "candidate/toy-mut-legacy",
                Path.of("/tmp/repo/.worktrees/candidate-toy-mut-legacy"),
                "legacy123");
        var evidence = new EvaluationEvidence(
                List.of(CheckEvidence.passed("workflow-check", "all good")),
                List.of(),
                Instant.parse("2026-08-01T00:00:00Z"));
        var objectives = Map.of(
                "task_success", 1.0,
                "hard_gate_deterministic_checks", 1.0);
        return new EvolveRunResult(
                new FitnessResult(candidate, evidence, objectives, FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT),
                Path.of("/tmp/repo/toy/journal.md"));
    }
}
