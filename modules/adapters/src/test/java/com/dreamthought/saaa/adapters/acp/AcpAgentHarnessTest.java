package com.dreamthought.saaa.adapters.acp;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.evolve.AgentHarnessMutationProposer;
import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRoute;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.ResourceBudget;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class AcpAgentHarnessTest {
    @ParameterizedTest
    @EnumSource(MutationScope.class)
    void offersOnlyTheScopesPassedThroughTheProposer(MutationScope scope, @TempDir Path dir) throws Exception {
        String prompt = capturePrompt(dir, Optional.of(Set.of(scope)));

        assertThat(prompt)
                .contains("Scope must be one of [" + scope + "].")
                .contains("The patch field must contain the complete new workflow file, not a diff.");
    }

    @Test
    void offersAllScopesByDefault(@TempDir Path dir) throws Exception {
        assertThat(capturePrompt(dir, Optional.empty()))
                .contains("Scope must be one of " + List.of(MutationScope.values()) + ".");
    }

    @Test
    void parsesTheBoundedMutationEnvelopeFromAgentText() {
        var mutation = AcpAgentHarness.parseMutation("Here is the proposal:"
                + " {\"id\":\"m-1\",\"summary\":\"tighten policy\","
                + "\"scope\":\"WORKFLOW_DEFINITION\",\"patch\":\"replace policy\"}");

        assertThat(mutation.id()).isEqualTo("m-1");
        assertThat(mutation.scope()).isEqualTo(WORKFLOW_DEFINITION);
    }

    @Test
    void rejectsMalformedMutationEnvelope() {
        assertThatThrownBy(() -> AcpAgentHarness.parseMutation("not JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("did not contain a JSON object");
    }

    @Test
    void refusesAnInvocationWhenTheBudgetIsExhausted() {
        var result = new AcpAgentHarness(new AcpAgentConfig("false", List.of())).run(request(
                new ResourceBudget(0, 100, BigDecimal.ONE, 10_000, 1)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.REJECTED);
        assertThat(result.failureReason()).contains("invocation resource budget is exhausted");
    }

    @Test
    void recordsAFailedAcpSubprocessWithoutCreatingAMutation() {
        var result = new AcpAgentHarness(new AcpAgentConfig("false", List.of())).run(request(
                new ResourceBudget(100, 100, BigDecimal.ONE, 1_000, 1)));

        assertThat(result.status()).isIn(AgentRunStatus.FAILED, AgentRunStatus.TIMED_OUT);
        assertThat(result.mutation()).isEmpty();
        assertThat(result.failureReason()).isPresent();
        assertThat(result.usage().inputTokens()).isEmpty();
        assertThat(result.usage().outputTokens()).isEmpty();
        assertThat(result.usage().creditsConsumed()).isEmpty();
        assertThat(result.usage().wallClockMillis()).isPresent();
        assertThat(result.usage().retries()).isEmpty();
        assertThat(result.usage().priceIdentity()).isEmpty();
    }

    private static String capturePrompt(Path dir, Optional<Set<MutationScope>> scopes) throws Exception {
        Path captured = dir.resolve("prompt.txt");
        Path agent = Files.writeString(dir.resolve("agent.py"), """
                import json, pathlib, sys

                def send(message):
                    print(json.dumps(dict(jsonrpc="2.0", **message)), flush=True)

                for line in sys.stdin:
                    request = json.loads(line)
                    method = request.get("method")
                    if method == "initialize":
                        result = {"protocolVersion": 1, "agentCapabilities": {}}
                    elif method == "session/new":
                        result = {"sessionId": "test-session"}
                    elif method == "session/prompt":
                        pathlib.Path(sys.argv[1]).write_text(request["params"]["prompt"][0]["text"])
                        result = {"stopReason": "end_turn"}
                    else:
                        continue
                    send({"id": request["id"], "result": result})
                """);
        MutationProposer proposer = new AgentHarnessMutationProposer(
                new AcpAgentHarness(new AcpAgentConfig("python3", List.of(agent.toString(), captured.toString()))),
                new AgentRoute("acp", "agent", "bounded", "scope test"),
                new ResourceBudget(100, 100, BigDecimal.ONE, 10_000, 0));
        if (scopes.isPresent()) {
            proposer = proposer.withAllowedScopes(scopes.orElseThrow());
        }
        // The stub captures the actual wire prompt and deliberately returns no proposal.
        MutationProposer configured = proposer;
        assertThatThrownBy(() -> configured.proposeFor(new WorkflowGraph("workflow", "rev-1", "definition")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACP agent response did not contain a JSON object");
        return Files.readString(captured);
    }

    private static AgentRequest request(ResourceBudget budget) {
        var baseline = new WorkflowGraph("workflow", "rev-1", "definition");
        var query = new RetrievalQuery(
                RetrievalMode.NONE, "bounded proposal", baseline, "rev-1", List.of(), Optional.empty());
        return new AgentRequest(
                new MutationProposalRequest(baseline, query),
                Path.of("build/candidate"),
                Set.of("read-workflow"),
                "mutation-v1",
                new AgentRoute("acp", "agent", "bounded", "test route"),
                budget);
    }
}
