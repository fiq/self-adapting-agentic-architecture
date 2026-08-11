package com.dreamthought.saaa.adapters.evolve;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRoute;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ResourceBudget;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MutationProposerAgentHarnessTest {
    @Test
    void exposesSuccessfulLegacyProposerAsAuditedCompletedRun() {
        var mutation = new Mutation("m-1", "bounded change", WORKFLOW_DEFINITION, "replacement");
        var harness = new MutationProposerAgentHarness(ignored -> mutation);

        var result = harness.run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.mutation()).contains(mutation);
        assertThat(result.route().reason()).isEqualTo("bounded test invocation");
    }

    @Test
    void capturesProposalFailureWithoutApprovingIt() {
        var harness = new MutationProposerAgentHarness(ignored -> {
            throw new IllegalStateException("provider unavailable");
        });

        var result = harness.run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.mutation()).isEmpty();
        assertThat(result.failureReason()).contains("provider unavailable");
    }

    @Test
    void rejectsAnInvocationWithAnExhaustedTokenAllowance() {
        var harness = new MutationProposerAgentHarness(ignored -> {
            throw new AssertionError("an exhausted invocation must not reach the proposer");
        });
        var request = requestWithBudget(new ResourceBudget(0, 500, BigDecimal.ONE, 10_000, 0));

        var result = harness.run(request);

        assertThat(result.status()).isEqualTo(AgentRunStatus.REJECTED);
        assertThat(result.failureReason()).contains("invocation resource budget is exhausted");
    }

    private static AgentRequest request() {
        return requestWithBudget(new ResourceBudget(1000, 500, BigDecimal.ONE, 10_000, 1));
    }

    private static AgentRequest requestWithBudget(ResourceBudget budget) {
        var baseline = new WorkflowGraph("workflow", "rev-1", "definition");
        var query = new RetrievalQuery(
                RetrievalMode.NONE, "bounded proposal", baseline, "rev-1",
                List.of(), Optional.empty());
        return new AgentRequest(
                new MutationProposalRequest(baseline, query),
                Path.of("build/candidate"),
                Set.of("read-workflow"),
                "mutation-v1",
                new AgentRoute("fixture", "fixture", "small", "bounded test invocation"),
                budget);
    }
}
