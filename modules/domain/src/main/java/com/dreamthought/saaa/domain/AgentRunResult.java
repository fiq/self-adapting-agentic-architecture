package com.dreamthought.saaa.domain;

import java.util.Objects;
import java.util.Optional;

/** Auditable outcome of an agent invocation; promotion remains outside this record. */
public record AgentRunResult(
        AgentRunStatus status,
        AgentRoute route,
        Optional<Mutation> mutation,
        Optional<String> sessionId,
        Optional<String> rawOutputDigest,
        AgentUsage usage,
        Optional<String> failureReason
) {
    public AgentRunResult {
        status = Objects.requireNonNull(status, "status");
        route = Objects.requireNonNull(route, "route");
        mutation = Objects.requireNonNull(mutation, "mutation");
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        rawOutputDigest = Objects.requireNonNull(rawOutputDigest, "rawOutputDigest");
        usage = Objects.requireNonNull(usage, "usage");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        if (status == AgentRunStatus.COMPLETED && mutation.isEmpty()) {
            throw new IllegalArgumentException("completed agent run must contain a mutation");
        }
        if (status != AgentRunStatus.COMPLETED && mutation.isPresent()) {
            throw new IllegalArgumentException("non-completed agent run must not contain a mutation");
        }
        if (status == AgentRunStatus.COMPLETED && failureReason.isPresent()) {
            throw new IllegalArgumentException("completed agent run must not contain a failure reason");
        }
        if (status != AgentRunStatus.COMPLETED && failureReason.isEmpty()) {
            throw new IllegalArgumentException("non-completed agent run must contain a failure reason");
        }
    }
}
