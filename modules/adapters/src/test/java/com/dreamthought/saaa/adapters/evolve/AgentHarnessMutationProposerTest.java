package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.AgentRoute;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.ResourceBudget;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AgentHarnessMutationProposerTest {
    @Test
    void returnsCompletedMutationAndExposesRouteEvidence() {
        var mutation = new Mutation("m-1", "bounded", MutationScope.WORKFLOW_DEFINITION, "changed");
        AgentHarness harness = request -> new AgentRunResult(
                AgentRunStatus.COMPLETED, request.route(), Optional.of(mutation), Optional.empty(),
                Optional.empty(), AgentUsage.none(), Optional.empty());
        var proposer = new AgentHarnessMutationProposer(
                harness, new AgentRoute("acp", "local", "small", "test"),
                new ResourceBudget(100, 100, BigDecimal.ONE, 1000, 0));

        assertThat(proposer.proposeFor(new WorkflowGraph("workflow", "rev", "definition"))).isEqualTo(mutation);
        assertThat(proposer.proposerEvidence()).get().satisfies(evidence -> {
            assertThat(evidence.attributes()).containsEntry("provider", "acp");
            assertThat(evidence.attributes()).containsEntry("model", "local");
        });
    }

    @Test
    void rejectsNonCompletedRunsBeforeCandidateCreation() {
        AgentHarness harness = request -> new AgentRunResult(
                AgentRunStatus.TIMED_OUT,
                request.route(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                AgentUsage.none(),
                Optional.of("agent deadline exceeded"));
        var proposer = new AgentHarnessMutationProposer(
                harness,
                new AgentRoute("acp", "configured", "bounded", "test"),
                new ResourceBudget(100, 100, BigDecimal.ONE, 1000, 0));

        assertThatThrownBy(() -> proposer.proposeFor(new WorkflowGraph("workflow", "rev", "definition")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIMED_OUT")
                .hasMessageContaining("agent deadline exceeded");
    }

    @Test
    void equalRawValuesWithDifferentAvailabilityAreRecordedDifferently() {
        var measuredZero = new AgentUsage(
                OptionalLong.of(0), OptionalLong.empty(), Optional.empty(),
                OptionalLong.empty(), OptionalInt.empty(), Optional.empty());
        var unobserved = AgentUsage.none();
        var invocation = new AtomicInteger();
        var mutation = new Mutation("m-usage", "bounded", MutationScope.WORKFLOW_DEFINITION, "changed");
        AgentHarness harness = request -> new AgentRunResult(
                AgentRunStatus.COMPLETED, request.route(), Optional.of(mutation), Optional.empty(),
                Optional.empty(), invocation.getAndIncrement() == 0 ? measuredZero : unobserved, Optional.empty());
        var proposer = new AgentHarnessMutationProposer(
                harness, new AgentRoute("acp", "configured", "small", "test"),
                new ResourceBudget(100, 100, BigDecimal.ONE, 1000, 0));
        var baseline = new WorkflowGraph("workflow", "rev", "definition");

        proposer.proposeFor(baseline);
        var firstUsage = proposer.usage();
        proposer.proposeFor(baseline);
        var secondUsage = proposer.usage();

        assertThat(firstUsage).isEqualTo(measuredZero);
        assertThat(secondUsage).isEqualTo(unobserved);
        assertThat(firstUsage).isNotEqualTo(secondUsage);
        assertThat(firstUsage.priceIdentity()).isEmpty();
        assertThat(secondUsage.priceIdentity()).isEmpty();
    }
}
