package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.MutationScope;
import java.util.Set;

/** Provider-neutral port for one bounded agent invocation. */
@FunctionalInterface
public interface AgentHarness {
    AgentRunResult run(AgentRequest request);

    /** A harness that does not prompt for a scope ignores this restriction. */
    default AgentHarness withAllowedScopes(Set<MutationScope> scopes) {
        return this;
    }
}
