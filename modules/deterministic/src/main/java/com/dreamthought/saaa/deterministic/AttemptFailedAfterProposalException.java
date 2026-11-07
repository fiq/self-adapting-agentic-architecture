package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.AgentUsage;
import java.util.Objects;

/**
 * An attempt failed after the proposer had answered, so its usage was spent but not yet recorded
 * against any candidate.
 *
 * <p>Carries the usage so a generation can record what the rejected attempt cost. A failure before
 * the proposer returned, such as a transport error, is not wrapped: there is no usage to carry, and
 * absence must stay distinguishable from a free attempt.
 */
public final class AttemptFailedAfterProposalException extends RuntimeException {
    private final transient AgentUsage usage;

    public AttemptFailedAfterProposalException(RuntimeException cause, AgentUsage usage) {
        super(Objects.requireNonNull(cause, "cause").getMessage(), cause);
        this.usage = Objects.requireNonNull(usage, "usage");
    }

    public AgentUsage usage() {
        return usage;
    }

    @Override
    public RuntimeException getCause() {
        return (RuntimeException) super.getCause();
    }
}
