package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentUsage;
import java.util.Objects;
import java.util.Optional;

/** Compatibility adapter that exposes an existing proposer through the harness contract. */
public final class MutationProposerAgentHarness implements AgentHarness {
    private final MutationProposer proposer;

    public MutationProposerAgentHarness(MutationProposer proposer) {
        this.proposer = Objects.requireNonNull(proposer, "proposer");
    }

    @Override
    public AgentRunResult run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.budget().inputTokensRemaining() == 0
                || request.budget().outputTokensRemaining() == 0
                || request.budget().wallClockMillisRemaining() == 0) {
            return new AgentRunResult(
                    AgentRunStatus.REJECTED,
                    request.route(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    AgentUsage.none(),
                    Optional.of("invocation resource budget is exhausted"));
        }
        try {
            var mutation = proposer.proposeFor(request.proposal().baseline());
            return new AgentRunResult(
                    AgentRunStatus.COMPLETED,
                    request.route(),
                    Optional.of(Objects.requireNonNull(mutation, "mutation")),
                    Optional.empty(),
                    Optional.empty(),
                    AgentUsage.none(),
                    Optional.empty());
        } catch (RuntimeException exception) {
            return new AgentRunResult(
                    AgentRunStatus.FAILED,
                    request.route(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    AgentUsage.none(),
                    Optional.of(failureMessage(exception)));
        }
    }

    private static String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String safe = message.replaceAll("(?i)bearer\\s+\\S+", "Bearer <redacted>")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "<redacted>")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        return safe.length() <= 512 ? safe : safe.substring(0, 512) + "…";
    }
}
