package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AgentRequestTest {
    @Test
    void rejectsNegativeResourceBudget() {
        assertThatThrownBy(() -> new ResourceBudget(-1, 0, BigDecimal.ZERO, 1000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresCompletedResultToCarryAMutation() {
        var route = new AgentRoute("fixture", "fixture", "small", "test route");
        assertThatThrownBy(() -> new AgentRunResult(
                AgentRunStatus.COMPLETED,
                route,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                AgentUsage.none(),
                java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresNonCompletedResultToCarryFailureEvidence() {
        var route = new AgentRoute("fixture", "fixture", "small", "test route");
        assertThatThrownBy(() -> new AgentRunResult(
                AgentRunStatus.FAILED,
                route,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                AgentUsage.none(),
                java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCreditsAndEveryNegativeUsageBudget() {
        assertThatThrownBy(() -> new ResourceBudget(0, 0, BigDecimal.valueOf(-1), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceBudget(0, 0, BigDecimal.ZERO, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceBudget(0, 0, BigDecimal.ZERO, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentUsage(0, 0, BigDecimal.valueOf(-1), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static AgentRequest request(WorkflowGraph baseline) {
        var query = new RetrievalQuery(
                RetrievalMode.NONE, "bounded proposal", baseline, "rev-1",
                List.of(), java.util.Optional.empty());
        return new AgentRequest(
                new MutationProposalRequest(baseline, query),
                Path.of("build/candidate"),
                Set.of("read-workflow"),
                "mutation-v1",
                new AgentRoute("fixture", "fixture", "small", "bounded test invocation"),
                new ResourceBudget(1000, 500, BigDecimal.ONE, 10_000, 1));
    }
}
