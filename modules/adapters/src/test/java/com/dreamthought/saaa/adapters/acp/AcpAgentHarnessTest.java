package com.dreamthought.saaa.adapters.acp;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRoute;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.ResourceBudget;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AcpAgentHarnessTest {
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
